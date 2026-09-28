package net.reichholf.dreamdroid.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.BOUQUET
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.CHANNEL
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.event
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [EpgRepository] against a [okhttp3.mockwebserver.MockWebServer] receiver and Room. */
class EpgRepositoryTest {
    private val receiver = EpgTestReceiver()
    private val repository = receiver.repository

    @BeforeEach
    fun setUp() {
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        receiver.stop()
    }

    @Test
    fun bouquetEventsAskTheReceiverAtTheInstant() = runBlocking {
        val loads = repository.bouquetEvents(BOUQUET, NOW).toList()

        assertEquals(listOf(false), loads.map { (it as EventListLoad.Events).cached })
        assertEquals(listOf("Tagesschau", "N/A"), titles(loads.single()))
        val url = receiver.server.takeRequest().requestUrl!!
        assertEquals("/web/epgbouquet", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(NOW.toString(), url.queryParameter("time"))
    }

    @Test
    fun onlineWithCachePaintsRoomThenTheReceiver() = runBlocking {
        receiver.writeChunk(BOUQUET, NOW, listOf(event("News", NOW)))

        val loads = repository.bouquetEvents(BOUQUET, NOW).toList()

        assertEquals(listOf(true, false), loads.map { (it as EventListLoad.Events).cached })
        assertEquals(listOf("News"), titles(loads[0]))
        assertEquals(listOf("Tagesschau", "N/A"), titles(loads[1]))
        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun offlineWithCachePaintsRoomWithoutAskingTheReceiver() = runBlocking {
        receiver.writeChunk(BOUQUET, NOW, listOf(event("News", NOW)))
        receiver.goOffline()

        val loads = repository.bouquetEvents(BOUQUET, NOW).toList()

        assertEquals(listOf(EventListLoad.Events(loads.single().events(), cached = true)), loads)
        assertEquals(listOf("News"), titles(loads.single()))
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun offlineWithoutCacheStillAsksTheReceiver() = runBlocking {
        receiver.writeChunk(
            OTHER_BOUQUET,
            NOW,
            listOf(event("Other", NOW, bouquetRef = OTHER_BOUQUET))
        )
        receiver.goOffline()

        val loads = repository.bouquetEvents(BOUQUET, NOW).toList()

        assertEquals(listOf("Tagesschau", "N/A"), titles(loads.single()))
        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun forcedRefreshSkipsRoomButFallsBackToItOnFailure() = runBlocking {
        receiver.writeChunk(BOUQUET, NOW, listOf(event("News", NOW)))
        receiver.goOffline()
        receiver.answer = { MockResponse().setResponseCode(500) }

        val loads = repository.bouquetEvents(BOUQUET, NOW, forceRefresh = true).toList()

        assertEquals(1, receiver.server.requestCount)
        assertEquals(listOf(true), loads.map { (it as EventListLoad.Events).cached })
        assertEquals(listOf("News"), titles(loads.single()))
    }

    @Test
    fun failureWithoutCacheReportsTheError() = runBlocking {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val load = repository.bouquetEvents(BOUQUET, NOW).toList().single()

        val failure = (load as EventListLoad.Failed).error?.failure
        assertTrue(failure is EnigmaFailure.Http)
        assertEquals(500, (failure as EnigmaFailure.Http).code)
    }

    @Test
    fun writtenEmptyChunkIsAnEmptyListNotMissing() = runBlocking {
        receiver.writeChunk(BOUQUET, NOW, emptyList())
        receiver.goOffline()

        val loads = repository.bouquetEvents(BOUQUET, NOW).toList()

        assertEquals(listOf(EventListLoad.Events(emptyList(), cached = true)), loads)
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun cacheIsPerProfileAndBouquet() = runBlocking {
        receiver.writeChunk(BOUQUET, NOW, listOf(event("News", NOW)))
        receiver.goOffline()

        repository.bouquetEvents(OTHER_BOUQUET, NOW).toList()
        receiver.profiles.repository.setCurrent(
            Profile().apply {
                id = EpgTestReceiver.PROFILE_ID + 1
                host = receiver.server.hostName
                port = receiver.server.port
            }
        )
        repository.bouquetEvents(BOUQUET, NOW).toList()

        assertEquals(2, receiver.server.requestCount)
    }

    @Test
    fun cachedBouquetIsOneProgrammePerChannelAtTheInstant() = runBlocking {
        receiver.writeChunk(
            BOUQUET,
            NOW,
            listOf(
                event("News", start = NOW, duration = 3600),
                event("Talk", start = NOW + 3600, duration = 3600),
                event("Match", start = NOW - 600, duration = 7200, service = OTHER),
                event("Studio", start = NOW + 6600, duration = 1800, service = OTHER)
            )
        )
        receiver.goOffline()

        val now = repository.bouquetEvents(BOUQUET, NOW).toList().single()
        val later = repository.bouquetEvents(BOUQUET, NOW + 4000).toList().single()

        assertEquals(listOf("News", "Match"), titles(now))
        assertEquals(listOf("Talk", "Match"), titles(later))
    }

    @Test
    fun serviceEventsAskTheReceiverForTheService() = runBlocking {
        val loads = repository.serviceEvents(CHANNEL).toList()

        assertEquals(listOf("Tagesschau", "N/A"), titles(loads.single()))
        val url = receiver.server.takeRequest().requestUrl!!
        assertEquals("/web/epgservice", url.encodedPath)
        assertEquals(CHANNEL, url.queryParameter("sRef"))
    }

    @Test
    fun offlineServiceEventsComeFromRoomFromNowOn() = runBlocking {
        val now = System.currentTimeMillis() / 1000L
        receiver.writeChunk(
            BOUQUET,
            now,
            listOf(
                event("Ended", start = now - 7200, duration = 3600),
                event("News", start = now - 600, duration = 3600),
                event("Other", start = now, service = OTHER)
            )
        )
        receiver.goOffline()

        val loads = repository.serviceEvents(CHANNEL).toList()

        assertEquals(listOf("News"), titles(loads.single()))
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun serviceMissingFromRoomAsksTheReceiverOffline() = runBlocking {
        receiver.goOffline()

        repository.serviceEvents(CHANNEL).toList()

        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun searchAsksTheReceiver() = runBlocking {
        val response = repository.search("Tagesschau")

        assertEquals("Tagesschau", response.value?.first()?.title)
        val url = receiver.server.takeRequest().requestUrl!!
        assertEquals("/web/epgsearch", url.encodedPath)
        assertEquals("Tagesschau", url.queryParameter("search"))
    }

    private fun EventListLoad.events() = (this as EventListLoad.Events).events

    private fun titles(load: EventListLoad) = load.events().map { it.title }

    private companion object {
        const val NOW = 1_893_456_000L
        const val OTHER = "1:0:1:6DCB:44C:1:C00000:0:0:0:"
        const val OTHER_BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet"
    }
}
