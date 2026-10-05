package net.reichholf.dreamdroid.data

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.EnigmaFailureException
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.BouquetTabEntity
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.MovieLocationStripEntity
import net.reichholf.dreamdroid.room.ServiceRosterEntity
import net.reichholf.dreamdroid.room.toListEntity
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.event
import net.reichholf.dreamdroid.testutil.RADIO_ROOTS
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ServiceRepository] against a MockWebServer receiver and Room. */
class ServiceRepositoryTest {
    private val receiver = EpgTestReceiver()
    private val services = receiver.services
    private val database = receiver.profiles.database
    private val rosterDao = database.rosterDao()

    /** `/web/getservices` answers by `sRef`; others get `getservices.xml`. */
    private val lists = HashMap<String, String>()

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
    fun bouquetsReadBothIndexesAndStoreTheirUserBouquetTabs() = runBlocking {
        lists[TV_ROOTS[0]] = serviceList(FAVOURITES to "Favourites", TV_ROOTS[1] to "Provider")
        lists[RADIO_ROOTS[0]] = serviceList(RADIO to "Radio")

        val load = services.bouquets().last() as BouquetListLoad.Loaded

        assertFalse(load.cached)
        assertEquals(listOf("Favourites", "Provider"), load.bouquets.tv.map { it.name })
        assertEquals(listOf("Radio"), load.bouquets.radio.map { it.name })
        assertEquals(
            listOf(TV_ROOTS[0], RADIO_ROOTS[0]),
            receiver.requestsTo(GET_SERVICES).map { it.sRef() }
        )
        assertEquals(listOf(FAVOURITES), strip("TV"))
        assertEquals(listOf(RADIO), strip("RADIO"))
        assertEquals(listOf(FAVOURITES, RADIO).sorted(), services.hubTabStripRefs().sorted())
        assertTrue(hasUseDrivenCache(services.hubTabStripRefs()))
    }

    @Test
    fun failedTvIndexFallsBackToTheTabStrips() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")
        receiver.answer = { MockResponse().setResponseCode(500) }

        val load = services.bouquets().last() as BouquetListLoad.Loaded

        assertTrue(load.cached)
        assertEquals(listOf("Favourites"), load.bouquets.tv.map { it.name })
        assertEquals(1, receiver.requestsTo(GET_SERVICES).size)
        assertEquals(listOf(FAVOURITES), strip("TV"))
    }

    @Test
    fun failedTvIndexWithoutTabStripFails() = runBlocking {
        receiver.answer = { MockResponse().setResponseCode(500) }

        assertTrue(services.bouquets().last() is BouquetListLoad.Failed)
        assertTrue(services.cachedBouquets().tv.isEmpty())
    }

    @Test
    fun failedTvIndexFailsEvenWhenRadioWouldAnswer() = runBlocking {
        receiver.answer = { request ->
            if (request.sRef() == TV_ROOTS[0]) {
                MockResponse().setResponseCode(500)
            } else {
                routes(request)
            }
        }

        assertTrue(services.bouquets().last() is BouquetListLoad.Failed)
        assertEquals(listOf(TV_ROOTS[0]), receiver.requestsTo(GET_SERVICES).map { it.sRef() })
    }

    @Test
    fun emptyIndexesAreAnEmptySuccess() = runBlocking {
        lists[TV_ROOTS[0]] = serviceList()
        lists[RADIO_ROOTS[0]] = serviceList()

        val load = services.bouquets().last() as BouquetListLoad.Loaded

        assertFalse(load.cached)
        assertTrue(load.bouquets.tv.isEmpty() && load.bouquets.radio.isEmpty())
    }

    @Test
    fun emptyServiceListIsAnEmptySuccess() = runBlocking {
        lists[FAVOURITES] = serviceList()

        val load = services.services(FAVOURITES).last() as ServiceListLoad.Services

        assertTrue(load.services.isEmpty())
        assertFalse(load.cached)
    }

    @Test
    fun failedRadioIndexKeepsTheRadioStripAndStoresTv() = runBlocking {
        writeStrip("RADIO", RADIO to "Radio")
        lists[TV_ROOTS[0]] = serviceList(FAVOURITES to "Favourites")
        receiver.answer = { request ->
            if (request.sRef() == RADIO_ROOTS[0]) {
                MockResponse().setResponseCode(500)
            } else {
                routes(request)
            }
        }

        val load = services.bouquets().last() as BouquetListLoad.Loaded

        assertFalse(load.cached)
        assertEquals(listOf("Favourites"), load.bouquets.tv.map { it.name })
        assertTrue(load.bouquets.radio.isEmpty())
        assertEquals(listOf(FAVOURITES), strip("TV"))
        assertEquals(listOf(RADIO), strip("RADIO"))
    }

    @Test
    fun offlineBouquetsComeFromTheTabStripsWithoutTheReceiver() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")
        receiver.goOffline()

        val load = services.bouquets().toList().single() as BouquetListLoad.Loaded

        assertTrue(load.cached)
        assertEquals(listOf("Favourites"), load.bouquets.tv.map { it.name })
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun offlineServicesComeFromTheRosterUntilAForcedRefresh() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")
        services.persistRoster(FAVOURITES, FAVOURITES, listOf(ServiceNowNext(CHANNEL, "Cached")))
        receiver.goOffline()

        val cached = services.services(FAVOURITES).toList().single() as ServiceListLoad.Services
        assertEquals(0, receiver.server.requestCount)
        val forced = services.services(FAVOURITES, forceRefresh = true).toList().single()

        assertEquals(listOf("Cached"), cached.services.map { it.name })
        assertEquals(3, (forced as ServiceListLoad.Services).services.size)
        assertFalse(forced.cached)
    }

    @Test
    fun tvBouquetsComeFromTheReceiverWithoutWritingTheStrip() = runBlocking {
        val bouquets = services.tvBouquets()

        assertTrue(bouquets.isNotEmpty())
        val request = receiver.requestsTo(GET_SERVICES).single()
        assertEquals(TV_ROOTS[0], request.sRef())
        assertNull(request.bRef())
        assertTrue(strip("TV").isEmpty())
    }

    @Test
    fun offlineTvBouquetsComeFromTheTabStrip() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")
        receiver.goOffline()

        assertEquals(listOf("Favourites"), services.tvBouquets().map { it.name })
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun tvHubBouquetsReplaceTheTvStrip() = runBlocking {
        writeStrip("TV", OTHER to "Other")
        lists[TV_ROOTS[0]] = serviceList(FAVOURITES to "Favourites", TV_ROOTS[2] to "All")

        val bouquets = services.tvBouquetTabs().value!!

        assertEquals(listOf("Favourites", "All"), bouquets.map { it.name })
        assertEquals(listOf(FAVOURITES), strip("TV"))
        assertEquals(listOf("Favourites"), services.cachedTvBouquetTabs().map { it.name })
    }

    @Test
    fun bouquetServicesComeFromGetservicesAndFailuresThrow() = runBlocking<Unit> {
        val bouquet = services.bouquetServices(FAVOURITES)

        assertTrue(bouquet.any { it.name == "Das Erste HD" })
        assertEquals(FAVOURITES, receiver.requestsTo(GET_SERVICES).single().sRef())

        receiver.answer = { MockResponse().setResponseCode(500) }
        assertThrows(EnigmaFailureException::class.java) {
            runBlocking { services.bouquetServices(FAVOURITES) }
        }
    }

    @Test
    fun servicesFallBackToTheWrittenRoster() = runBlocking {
        val live = services.services(FAVOURITES).last() as ServiceListLoad.Services
        assertFalse(live.cached)
        assertEquals(3, live.services.size)

        receiver.answer = { MockResponse().setResponseCode(500) }
        assertTrue(services.services(FAVOURITES).last() is ServiceListLoad.Failed)

        writeStrip("TV", FAVOURITES to "Favourites")
        services.persistRoster(FAVOURITES, FAVOURITES, listOf(ServiceNowNext(CHANNEL, "Cached")))
        val cached = services.services(FAVOURITES).last() as ServiceListLoad.Services
        assertTrue(cached.cached)
        assertEquals(listOf("Cached"), cached.services.map { it.name })
        assertEquals(
            listOf("Cached"),
            services.cachedBouquetServices(PROFILE_ID, FAVOURITES)?.map {
                it.name
            }
        )
    }

    @Test
    fun nowNextMergesTheRosterWithTheBouquetEpg() = runBlocking {
        val rows = services.receiverNowNext(FAVOURITES).value!!

        assertEquals(
            listOf("Favourites (TV)", "Das Erste HD", "--------"),
            rows.map {
                it.serviceName
            }
        )
        assertEquals("News Now", rows[1].now?.title)
        assertNull(rows[0].now)
        assertEquals(FAVOURITES, receiver.requestsTo(GET_SERVICES).single().sRef())
        assertEquals(FAVOURITES, receiver.requestsTo(EPG_NOW_NEXT).single().bRef())
    }

    @Test
    fun nowNextOfANonBouquetContainerAsksEpgByBRef() = runBlocking {
        val list = "1:0:1:0:0:0:0:0:0:0:FROM PROVIDERS"
        services.receiverNowNext(list)

        assertEquals(list, receiver.requestsTo(GET_SERVICES).single().sRef())
        val epg = receiver.requestsTo(EPG_NOW_NEXT).single()
        assertEquals(list, epg.bRef())
        assertNull(epg.sRef())
    }

    @Test
    fun failedEpgKeepsTheRosterAndFailedRosterFails() = runBlocking {
        receiver.answer = { request ->
            if (request.requestUrl?.encodedPath == EPG_NOW_NEXT) {
                MockResponse().setResponseCode(500)
            } else {
                routes(request)
            }
        }
        val rows = services.receiverNowNext(FAVOURITES).value!!
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.now == null && it.next == null })

        receiver.answer = { MockResponse().setResponseCode(500) }
        val failed = services.receiverNowNext(FAVOURITES)
        assertNull(failed.value)
        assertNotNull(failed.error)
    }

    @Test
    fun tabRosterStoresRowKinds() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites", SPORTS to "Sports")

        assertTrue(services.persistRoster(FAVOURITES, FAVOURITES, listOf(channel, folder, marker)))

        val stored = rosterDao.getRoster(PROFILE_ID, FAVOURITES)
        assertEquals(
            listOf(
                EnigmaService.ROSTER_KIND_CHANNEL,
                EnigmaService.ROSTER_KIND_DIRECTORY,
                EnigmaService.ROSTER_KIND_MARKER
            ),
            stored.map { it.kind }
        )
        assertEquals(
            listOf("Das Erste HD", "Satellites", "--------"),
            services.cachedNowNext(FAVOURITES)?.map { it.serviceName }
        )
    }

    @Test
    fun folderUnderAHubTabIsStored() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")

        assertTrue(services.persistRoster(FOLDER, FAVOURITES, listOf(channel)))
        assertEquals(listOf("Das Erste HD"), services.cachedNowNext(FOLDER)?.map { it.serviceName })
    }

    @Test
    fun dedicatedRootsAndTheirFoldersAreNeverStored() = runBlocking {
        lists[TV_ROOTS[0]] = serviceList(FAVOURITES to "Favourites", TV_ROOTS[1] to "Provider")
        lists[RADIO_ROOTS[0]] = serviceList()
        services.bouquets().last()

        for (root in TV_ROOTS + RADIO_ROOTS) {
            assertFalse(services.persistRoster(root, root, listOf(channel)))
            assertEquals(0, rosterDao.rosterContainerCount(PROFILE_ID, root))
        }
        assertFalse(services.persistRoster(FOLDER, TV_ROOTS[2], listOf(channel)))
        assertFalse(services.persistRoster(FOLDER, TV_ROOTS[1], listOf(channel)))
        assertNull(services.cachedNowNext(FOLDER))
        assertTrue(strip("TV").none { it in TV_ROOTS })
    }

    @Test
    fun containerOutsideTheTabStripIsNotStored() = runBlocking {
        assertFalse(services.persistRoster(FAVOURITES, FAVOURITES, listOf(channel)))

        writeStrip("TV", SPORTS to "Sports")
        assertFalse(services.persistRoster(FAVOURITES, FAVOURITES, listOf(channel)))
        assertNull(services.cachedNowNext(FAVOURITES))
    }

    @Test
    fun writtenEmptyRosterIsEmptyNotMissing() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")

        assertTrue(services.persistRoster(FAVOURITES, FAVOURITES, emptyList()))

        assertEquals(emptyList<ServiceNowNext>(), services.cachedNowNext(FAVOURITES))
        assertNull(services.cachedNowNext(FOLDER))
    }

    @Test
    fun cachedRosterTakesNowAndNextFromRoomEpg() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")
        services.persistRoster(FAVOURITES, FAVOURITES, listOf(channel, folder))
        receiver.writeChunk(
            FAVOURITES,
            NOW,
            listOf(
                event("Now", NOW - 600, service = CHANNEL, bouquetRef = FAVOURITES),
                event("Later", NOW + 3000, service = CHANNEL, bouquetRef = FAVOURITES)
            )
        )

        val rows = services.cachedNowNext(FAVOURITES, NOW)!!

        assertEquals("Now", rows[0].now?.title)
        assertEquals("Later", rows[0].next?.title)
        assertNull(rows[1].now)
    }

    @Test
    fun offlineListPaintsRoomAndSkipsTheReceiver() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")
        services.persistRoster(FAVOURITES, FAVOURITES, listOf(channel))
        receiver.goOffline()

        val loads = services.nowNextList(FAVOURITES, FAVOURITES).toList()

        assertEquals(listOf(NowNextListLoad.Rows(listOf(channel), cached = true)), loads)
        assertEquals(0, receiver.server.requestCount)
    }

    @Test
    fun liveListReplacesTheRosterOfACacheableList() = runBlocking {
        writeStrip("TV", FAVOURITES to "Favourites")
        services.persistRoster(FAVOURITES, FAVOURITES, listOf(channel))

        val loads = services.nowNextList(FAVOURITES, FAVOURITES).toList()

        assertEquals(listOf(true, false), loads.map { (it as NowNextListLoad.Rows).cached })
        assertEquals(3, services.cachedNowNext(FAVOURITES)?.size)
    }

    @Test
    fun hasCacheCountsTabStripsMovieLocationsAndTimerSnapshots() = runBlocking {
        assertFalse(services.hasCache(PROFILE_ID))
        database.epgDao().replaceChunk(
            EpgChunkMetaEntity(PROFILE_ID, FAVOURITES, chunk.startSec, chunk.endSec, 1L),
            listOf(event("News", NOW, service = CHANNEL, bouquetRef = FAVOURITES))
        )
        assertFalse(services.hasCache(PROFILE_ID))

        writeStrip("RADIO", RADIO to "Radio")
        assertTrue(services.hasCache(PROFILE_ID))

        database.movieDao().replaceLocations(
            OTHER_PROFILE,
            listOf(MovieLocationStripEntity(OTHER_PROFILE, 0, HDD))
        )
        assertTrue(services.hasCache(OTHER_PROFILE))

        database.timerDao().replaceSnapshot(
            OTHER_PROFILE + 1,
            listOf(Timer(reference = CHANNEL, name = "News").toListEntity(OTHER_PROFILE + 1, 0))
        )
        assertTrue(services.hasCache(OTHER_PROFILE + 1))
    }

    @Test
    fun clearingTheActiveProfileKeepsOtherProfiles() = runBlocking {
        seed(PROFILE_ID)
        seed(OTHER_PROFILE)
        receiver.goOffline()

        services.clearUseDrivenCache(allProfiles = false)

        assertFalse(services.hasCache(PROFILE_ID))
        assertTrue(services.hasCache(OTHER_PROFILE))
        assertEquals(0, rosterDao.rosterContainerCount(PROFILE_ID, FAVOURITES))
        assertEquals(1, rosterDao.rosterContainerCount(OTHER_PROFILE, FAVOURITES))
        assertNull(database.epgDao().getChunk(PROFILE_ID, FAVOURITES, chunk.startSec))
        assertNotNull(database.epgDao().getChunk(OTHER_PROFILE, FAVOURITES, chunk.startSec))
        assertEquals(0, database.movieDao().movieListMetaCount(PROFILE_ID, HDD))
        assertEquals(1, database.movieDao().movieListMetaCount(OTHER_PROFILE, HDD))
        assertNull(receiver.sessions.status.value.session)
    }

    @Test
    fun clearingAllProfilesDropsEverything() = runBlocking {
        seed(PROFILE_ID)
        seed(OTHER_PROFILE)
        receiver.sessions.onSuccess()

        services.clearUseDrivenCache(allProfiles = true)

        assertFalse(services.hasCache(PROFILE_ID))
        assertFalse(services.hasCache(OTHER_PROFILE))
        assertEquals(0, rosterDao.rosterContainerCount(OTHER_PROFILE, FAVOURITES))
        assertNull(database.epgDao().getChunk(OTHER_PROFILE, FAVOURITES, chunk.startSec))
        assertNull(database.timerDao().snapshot(OTHER_PROFILE))
        assertEquals(0, database.movieDao().movieListMetaCount(OTHER_PROFILE, HDD))
        assertEquals(ConnectionStatus.Session.Online, receiver.sessions.status.value.session)
    }

    @Test
    fun deletingAProfileDropsItsCache() = runBlocking {
        val profiles = receiver.profiles.repository
        val other = Profile.getDefault().apply {
            name = "other"
            host = "10.0.0.2"
        }
        profiles.save(other)
        seed(other.id!!)
        seed(PROFILE_ID)

        profiles.delete(other)

        assertFalse(services.hasCache(other.id!!))
        assertTrue(services.hasCache(PROFILE_ID))
    }

    private val chunk = MultiEpgWindows.chunkContaining(NOW)
    private val channel = ServiceNowNext(CHANNEL, "Das Erste HD")
    private val folder = ServiceNowNext(FOLDER, "Satellites")
    private val marker = ServiceNowNext("1:64:1:0:0:0:0:0:0:0:", "--------")

    private suspend fun strip(kind: String): List<String> =
        rosterDao.getTabStrip(PROFILE_ID, kind).map { it.serviceRef }

    private suspend fun writeStrip(kind: String, vararg tabs: Pair<String, String>) {
        writeStrip(PROFILE_ID, kind, *tabs)
    }

    private suspend fun writeStrip(
        profileId: Int,
        kind: String,
        vararg tabs: Pair<String, String>
    ) {
        rosterDao.replaceTabStrip(
            profileId,
            kind,
            tabs.mapIndexed { index, (ref, name) ->
                BouquetTabEntity(profileId, kind, index, ref, name)
            }
        )
    }

    /** Every use-driven dataset of [profileId]: tab strip, roster, EPG, timers, movies. */
    private suspend fun seed(profileId: Int) {
        writeStrip(profileId, "TV", FAVOURITES to "Favourites")
        rosterDao.replaceRoster(
            profileId,
            FAVOURITES,
            listOf(
                ServiceRosterEntity(profileId, FAVOURITES, 0, CHANNEL, "Das Erste HD", "channel")
            )
        )
        database.epgDao().replaceChunk(
            EpgChunkMetaEntity(profileId, FAVOURITES, chunk.startSec, chunk.endSec, 1L),
            listOf(
                event("News", NOW, service = CHANNEL, bouquetRef = FAVOURITES)
                    .copy(profileId = profileId)
            )
        )
        database.timerDao().replaceSnapshot(
            profileId,
            listOf(
                Timer(reference = CHANNEL, serviceName = "Das Erste HD", name = "News")
                    .toListEntity(profileId, 0)
            )
        )
        database.movieDao().replaceLocations(
            profileId,
            listOf(MovieLocationStripEntity(profileId, 0, HDD))
        )
        database.movieDao().replaceMovies(
            profileId,
            HDD,
            listOf(
                Movie(
                    reference = "1:0:0:0:0:0:0:0:0:0:$HDD/news.ts",
                    title = "News"
                ).toListEntity(profileId, HDD, 0)
            )
        )
    }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.encodedPath) {
            GET_SERVICES -> MockResponse().setBody(
                lists[request.sRef()] ?: loadWebFixture("getservices.xml")
            )

            EPG_NOW_NEXT -> MockResponse().setBody(loadWebFixture("epgnownext.xml"))

            else -> MockResponse().setResponseCode(404)
        }

    private fun serviceList(vararg services: Pair<String, String>): String =
        services.joinToString("", "<e2servicelist>", "</e2servicelist>") { (ref, name) ->
            "<e2service><e2servicereference>${ref.xml()}</e2servicereference>" +
                "<e2servicename>$name</e2servicename></e2service>"
        }

    private fun String.xml(): String = replace("&", "&amp;").replace("\"", "&quot;")

    private fun RecordedRequest.sRef(): String? = requestUrl?.queryParameter("sRef")

    private fun RecordedRequest.bRef(): String? = requestUrl?.queryParameter("bRef")

    private companion object {
        const val OTHER_PROFILE = 8
        const val NOW = 1_893_456_000L + 600L
        const val HDD = "/media/hdd/movie"
        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val SPORTS =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet"
        const val OTHER =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
        const val RADIO =
            "1:7:2:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.radio\" ORDER BY bouquet"
        const val FOLDER = "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
        const val CHANNEL = "1:0:1:6DCA:44D:1:C00000:0:0:0:"
        const val GET_SERVICES = "/web/getservices"
        const val EPG_NOW_NEXT = "/web/epgnownext"
    }
}
