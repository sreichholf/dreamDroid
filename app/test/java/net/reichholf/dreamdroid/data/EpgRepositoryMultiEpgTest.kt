package net.reichholf.dreamdroid.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.EnigmaFailureException
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.BOUQUET
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.event
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The MultiEPG side of [EpgRepository] against a MockWebServer receiver and Room. */
class EpgRepositoryMultiEpgTest {
    private val receiver = EpgTestReceiver()
    private val repository = receiver.repository
    private val sync = repository.multiEpgSync
    private val chunk = MultiEpgWindows.chunkContaining(NOW)

    @BeforeEach
    fun setUp() {
        receiver.answer = ::routes
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        receiver.stop()
    }

    @Test
    fun oneSyncPerRepository() {
        assertSame(sync, repository.multiEpgSync)
    }

    @Test
    fun chunkFetchAsksForTheBoundedDayAndStoresIt() = runBlocking {
        val events = sync.ensureChunk(PROFILE_ID, BOUQUET, NOW)

        assertEquals(listOf("Tagesschau", "heute"), events.map { it.title })
        val url = receiver.server.takeRequest().requestUrl!!
        assertEquals("/web/epgmulti", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(chunk.startSec.toString(), url.queryParameter("time"))
        assertEquals("1440", url.queryParameter("endTime"))
        val peek = sync.peekChunk(PROFILE_ID, BOUQUET, NOW)
        assertNotNull(peek)
        assertTrue(peek!!.fresh)
        assertEquals(2, peek.events.size)
    }

    @Test
    fun freshChunkIsReusedWithoutAskingTheReceiver() = runBlocking {
        sync.ensureChunk(PROFILE_ID, BOUQUET, NOW)
        val again = sync.ensureChunk(PROFILE_ID, BOUQUET, NOW + 3600)

        assertEquals(2, again.size)
        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun forcedRefreshAsksAgain() = runBlocking {
        sync.ensureChunk(PROFILE_ID, BOUQUET, NOW)
        sync.ensureChunk(PROFILE_ID, BOUQUET, NOW, forceRefresh = true)

        assertEquals(2, receiver.server.requestCount)
    }

    @Test
    fun concurrentMissesOfOneChunkShareOneRequest() = runBlocking {
        val release = CountDownLatch(1)
        receiver.answer = { request ->
            release.await(5, TimeUnit.SECONDS)
            routes(request)
        }

        val first = async(Dispatchers.IO) { sync.ensureChunk(PROFILE_ID, BOUQUET, NOW) }
        val second = async(Dispatchers.IO) { sync.ensureChunk(PROFILE_ID, BOUQUET, NOW + 60) }
        Thread.sleep(200)
        release.countDown()

        assertEquals(listOf(2, 2), awaitAll(first, second).map { it.size })
        assertEquals(1, receiver.server.requestCount)
    }

    @Test
    fun oneEpgMultiRequestAtATime() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        receiver.answer = { request ->
            val now = active.incrementAndGet()
            maxActive.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            Thread.sleep(150)
            active.decrementAndGet()
            routes(request)
        }

        listOf(NOW, NOW + MultiEpgWindows.CHUNK_SECONDS, NOW + 2 * MultiEpgWindows.CHUNK_SECONDS)
            .map { at -> async(Dispatchers.IO) { sync.ensureChunk(PROFILE_ID, BOUQUET, at) } }
            .awaitAll()

        assertEquals(3, receiver.server.requestCount)
        assertEquals(1, maxActive.get())
    }

    @Test
    fun unpersistedChunkIsNotWritten() = runBlocking {
        val events = sync.ensureChunk(PROFILE_ID, BOUQUET, NOW, persist = false)

        assertEquals(2, events.size)
        assertNull(sync.peekChunk(PROFILE_ID, BOUQUET, NOW))
    }

    @Test
    fun failedFetchThrows() {
        receiver.answer = { MockResponse().setResponseCode(500) }

        assertThrows(EnigmaFailureException::class.java) {
            runBlocking { sync.ensureChunk(PROFILE_ID, BOUQUET, NOW) }
        }
    }

    @Test
    fun pruneDropsEventsThatEndedTwoDaysAgo() = runBlocking {
        val old = NOW - 3 * 24 * 3600L
        receiver.writeChunk(BOUQUET, old, listOf(event("Old", old)))
        receiver.writeChunk(BOUQUET, NOW, listOf(event("Current", NOW)))

        repository.pruneExpiredMultiEpgCache(nowSec = NOW)

        assertNull(sync.peekChunk(PROFILE_ID, BOUQUET, old))
        assertEquals(
            listOf("Current"),
            sync.peekChunk(PROFILE_ID, BOUQUET, NOW)!!.events.map { it.title }
        )
    }

    @Test
    fun bouquetServicesComeFromGetservices() = runBlocking {
        val services = repository.bouquetServices(BOUQUET)

        assertTrue(services.any { it.name == "Das Erste HD" })
        val url = receiver.server.takeRequest().requestUrl!!
        assertEquals("/web/getservices", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("sRef"))
    }

    @Test
    fun persistGateKnowsOnlyTheHubTabStrip() = runBlocking {
        val gate = repository.multiEpgPersistGate()
        assertEquals(false, gate.persist(BOUQUET))

        UserBouquetCache.replaceTabStrip(
            receiver.profiles.database.rosterDao(),
            PROFILE_ID,
            UserBouquetCache.KIND_TV,
            listOf(Service(BOUQUET, "Favourites")),
            emptySet()
        )
        gate.knownTabRefs = repository.hubTabStripRefs()

        assertEquals(true, gate.persist(BOUQUET))
        assertEquals(false, gate.persist(OTHER_BOUQUET))
    }

    @Test
    fun tvBouquetsComeFromTheReceiver() = runBlocking {
        val bouquets = repository.tvBouquets()

        assertTrue(bouquets.isNotEmpty())
        assertEquals("/web/getservices", receiver.server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun offlineTvBouquetsComeFromTheTabStrip() = runBlocking {
        UserBouquetCache.replaceTabStrip(
            receiver.profiles.database.rosterDao(),
            PROFILE_ID,
            UserBouquetCache.KIND_TV,
            listOf(Service(BOUQUET, "Favourites")),
            emptySet()
        )
        receiver.goOffline()

        val bouquets = repository.tvBouquets()

        assertEquals(listOf("Favourites"), bouquets.map { it.name })
        assertEquals(0, receiver.server.requestCount)
    }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.encodedPath) {
            "/web/epgmulti" -> MockResponse().setBody(loadWebFixture("epgmulti.xml"))
            "/web/getservices" -> MockResponse().setBody(loadWebFixture("getservices.xml"))
            else -> MockResponse().setResponseCode(404)
        }

    companion object {
        /** Inside the fixture's events, so they overlap the chunk. */
        const val NOW = 1_893_456_000L + 600L
        const val OTHER_BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
    }
}
