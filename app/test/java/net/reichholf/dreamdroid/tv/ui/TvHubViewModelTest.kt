package net.reichholf.dreamdroid.tv.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LastLiveStream
import net.reichholf.dreamdroid.data.LastLiveStreamRepository
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ReceiverPluginsRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.PROFILE_ID
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.jobs
import net.reichholf.dreamdroid.testutil.joinJobsSince
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.receiverApis
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TvHubViewModel] over the real repositories, a MockWebServer receiver, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvHubViewModelTest {
    private val receiver = EpgTestReceiver()
    private val clients = receiverApis(receiver.profiles.repository)
    private val timers = TimerRepository(
        clients,
        receiver.profiles.repository,
        receiver.profiles.database,
        ReceiverPluginsRepository(clients, receiver.profiles.repository),
        receiver.sessions
    )
    private val movies = MovieRepository(
        receiver.profiles.context,
        clients,
        receiver.profiles.repository,
        receiver.profiles.database,
        receiver.sessions,
        timers
    )
    private val lastStreams =
        LastLiveStreamRepository(MemorySharedPreferences(), receiver.profiles.repository)
    private val viewModels = mutableListOf<TvHubViewModel>()

    /** The TV bouquet index the receiver answers; empty means none. */
    @Volatile
    private var bouquets = listOf(FAVOURITES to "Favourites")

    @Volatile
    private var receiverDown = false

    /** `epgmulti` answers with programmes inside each requested window. */
    @Volatile
    private var epgInEveryWindow = false

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = ::routes
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun onlineLoadShowsRowsAndWritesTheCache() = runBlocking<Unit> {
        val state = awaitLoaded(viewModel())

        assertEquals(listOf(FAVOURITES), state.bouquetRows.map { it.bouquet.reference })
        val row = state.bouquetRows.single().services.map { it.serviceReference }
        assertEquals(listOf(FAVOURITES, CHANNEL, MARKER), row)
        assertEquals(listOf(HDD), state.movieLocations)
        assertNull(state.errorText)
        assertNotNull(receiver.services.cachedNowNext(FAVOURITES))
        assertEquals(listOf(HDD), movies.cachedLocations())
        assertTrue(receiver.services.hasCache(PROFILE_ID))
        assertEquals(1, receiver.requestsTo(EPG_MULTI).size)
    }

    @Test
    fun withoutAnActiveProfileTheHubSettlesEmptyAndAsksNothing() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        val asked = receiver.requests.size
        receiver.profiles.repository.clearCurrent()

        val before = viewModel.jobs()
        viewModel.reload()
        viewModel.joinJobsSince(before)

        val state = viewModel.uiState.value
        assertTrue(state.bouquetRows.isEmpty())
        assertEquals(emptyList<String>(), state.movieLocations)
        assertNull(state.errorText)
        assertEquals(asked, receiver.requests.size)
    }

    /** The last profile can be deleted while a browse waits for the receiver. */
    @Test
    fun aProfileDeletedBeforeTheBouquetsAreAskedLeavesTheHubEmpty() = runBlocking<Unit> {
        receiver.answer = { request ->
            if (request.requestUrl?.encodedPath == "/web/getlocations") {
                receiver.profiles.repository.clearCurrent()
            }
            routes(request)
        }

        val state = awaitLoaded(viewModel())

        assertTrue(state.bouquetRows.isEmpty())
        assertTrue(receiver.requestsTo(BOUQUET_INDEX_PATH).isEmpty())
    }

    /** Afterwards a bouquet has neither a receiver nor a profile to read Room with. */
    @Test
    fun aProfileDeletedMidBrowseDropsTheRemainingBouquets() = runBlocking<Unit> {
        bouquets = listOf(FAVOURITES to "Favourites", OTHER to "Other")
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        assertNotNull(receiver.services.cachedNowNext(OTHER))
        receiver.answer = { request ->
            val path = request.requestUrl?.encodedPath
            if (path == "/web/epgnownext" && request.bRef() == FAVOURITES) {
                receiver.profiles.repository.clearCurrent()
            }
            routes(request)
        }

        viewModel.reload()

        val state = awaitState(viewModel) { !it.loading && it.rowRefs() == listOf(FAVOURITES) }
        assertEquals(listOf(FAVOURITES), state.rowRefs())
    }

    @Test
    fun shownHubSyncsTheWeekOfTheSelectedBouquet() = runBlocking<Unit> {
        epgInEveryWindow = true
        bouquets = listOf(FAVOURITES to "Favourites", OTHER to "Other")
        val viewModel = viewModel(epgSyncDelayMs = 0L)
        awaitLoaded(viewModel)
        val browseFills = receiver.requestsTo(EPG_MULTI)
        assertEquals(2, browseFills.size)

        viewModel.selectDrawerItem(OTHER)
        viewModel.onShown()

        // Only OTHER's requests: the bouquet change can reach the sync after a brief start for
        // FAVOURITES under the unconfined test Main. syncWaitsUntilTheTargetSettles covers that.
        val synced = receiver.awaitRequests(MultiEpgWindows.SYNC_CHUNKS) {
            it.requestUrl?.encodedPath == EPG_MULTI && it.bRef() == OTHER
        }
        val today = browseFills.first().time()
        assertEquals(
            (0 until MultiEpgWindows.SYNC_CHUNKS).map {
                today + it * MultiEpgWindows.CHUNK_SECONDS
            },
            synced.map { it.time() }
        )
    }

    @Test
    fun syncWaitsUntilTheTargetSettles() = runTest {
        val synced = mutableListOf<String>()
        val targets = MutableStateFlow<String?>(null)
        val job = launch {
            targets.syncWhenSettled(TvHubViewModel.EPG_SYNC_DELAY_MS) { synced += it }
        }
        runCurrent()

        targets.value = FAVOURITES
        advanceTimeBy(TvHubViewModel.EPG_SYNC_DELAY_MS / 2)
        targets.value = OTHER
        advanceTimeBy(TvHubViewModel.EPG_SYNC_DELAY_MS - 1L)
        assertTrue(synced.isEmpty())
        advanceTimeBy(2L)
        assertEquals(listOf(OTHER), synced)

        targets.value = null
        targets.value = FAVOURITES
        advanceTimeBy(TvHubViewModel.EPG_SYNC_DELAY_MS / 2)
        targets.value = null
        advanceUntilIdle()
        assertEquals(listOf(OTHER), synced)
        job.cancel()
    }

    @Test
    fun offlineWithCachePaintsRoomWithoutAskingTheReceiver() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        val asked = receiver.requests.size

        receiver.goOffline()
        val state = awaitState(viewModel) {
            it.connection.session == ConnectionStatus.Session.Offline && !it.loading
        }

        assertEquals(asked, receiver.requests.size)
        assertEquals(listOf(FAVOURITES), state.bouquetRows.map { it.bouquet.reference })
        assertEquals(listOf(HDD), state.movieLocations)
        assertNull(state.errorText)
    }

    @Test
    fun failedReceiverWithoutCacheReportsAndReloadRecovers() = runBlocking<Unit> {
        receiverDown = true
        val viewModel = viewModel()

        val failed = awaitLoaded(viewModel)
        assertTrue(failed.bouquetRows.isEmpty())
        assertTrue(failed.errorText is UiText.Resource)

        receiverDown = false
        viewModel.reload()
        val recovered = awaitState(viewModel) { !it.loading && it.bouquetRows.isNotEmpty() }

        assertNull(recovered.errorText)
    }

    @Test
    fun failedReceiverWithCachePaintsRoom() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        receiverDown = true
        val before = viewModel.jobs()

        viewModel.reload()
        viewModel.joinJobsSince(before)

        assertEquals(2, receiver.requestsTo(BOUQUET_INDEX_PATH).count { it.isBouquetIndex() })
        val state = awaitLoaded(viewModel)
        assertEquals(listOf(FAVOURITES), state.bouquetRows.map { it.bouquet.reference })
        assertNull(state.errorText)
    }

    @Test
    fun checkingPaintsRoomAndAsksTheReceiverOnceThroughOnline() = runBlocking<Unit> {
        val first = viewModel()
        awaitLoaded(first)
        first.cancelAndJoin()
        receiver.sessions.resetForProfileChange()

        val viewModel = viewModel()
        assertEquals(listOf(FAVOURITES), awaitLoaded(viewModel).rowRefs())
        receiver.awaitRequests(2) { it.isBouquetIndex() }
        val before = viewModel.jobs()
        receiver.sessions.onSuccess()
        val started = viewModel.jobs() - before
        viewModel.joinJobsSince(before)

        assertTrue(started.isEmpty(), "Online after Checking started $started")
        assertEquals(2, receiver.requestsTo(BOUQUET_INDEX_PATH).count { it.isBouquetIndex() })
    }

    @Test
    fun aMovieLocationOnlyRoomAnsweredIsAskedAgain() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        movies.saveMovies(HDD, listOf(Movie(title = "Cached")))
        receiverDown = true
        var before = viewModel.jobs()
        viewModel.openTab(HubTab.Recordings)
        viewModel.joinJobsSince(before)
        // uiState combines the ViewModel state with the session, so it can trail the load.
        val cached = awaitState(viewModel) { HDD in it.moviesByLocation }
        assertEquals(listOf("Cached"), cached.moviesByLocation.getValue(HDD).map { it.title })
        viewModel.openTab(HubTab.Live)
        receiverDown = false
        before = viewModel.jobs()

        viewModel.openTab(HubTab.Recordings)
        viewModel.joinJobsSince(before)

        assertEquals(2, receiver.requestsTo(MOVIE_LIST).size)
        awaitState(viewModel) { it.moviesByLocation[HDD]?.size == 2 }
    }

    @Test
    fun recordingsLoadItsLocationOnce() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        viewModel.openTab(HubTab.Recordings)
        awaitState(viewModel) { HDD in it.moviesByLocation }
        viewModel.openTab(HubTab.Live)
        viewModel.openTab(HubTab.Recordings)
        val state = awaitState(viewModel) { !it.movieLoading }

        assertEquals(2, state.moviesByLocation.getValue(HDD).size)
        assertEquals(1, receiver.requestsTo(MOVIE_LIST).size)
        assertEquals(listOf(HDD), receiver.requestsTo(MOVIE_LIST).map { it.dirname() })
    }

    @Test
    fun offlineMoviesComeFromRoom() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        viewModel.openTab(HubTab.Recordings)
        awaitState(viewModel) { HDD in it.moviesByLocation }

        receiver.goOffline()
        val state = awaitState(viewModel) {
            it.connection.session == ConnectionStatus.Session.Offline &&
                !it.loading && HDD in it.moviesByLocation
        }

        assertEquals(HubSection.Recordings(HDD), state.section)
        assertEquals(2, state.moviesByLocation.getValue(HDD).size)
        assertEquals(1, receiver.requestsTo(MOVIE_LIST).size)
    }

    @Test
    fun onlyASessionChangeReloads() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        // Main is unconfined: a reload would launch its browse job inside onSuccess().
        // Joining what it started means any request it sent has arrived.
        val before = viewModel.jobs()
        receiver.sessions.onSuccess()
        val started = viewModel.jobs() - before
        viewModel.joinJobsSince(before)
        assertTrue(started.isEmpty(), "the same session started $started")
        assertEquals(1, receiver.requestsTo(EPG_MULTI).size)

        receiver.goOffline()
        awaitState(viewModel) {
            it.connection.session == ConnectionStatus.Session.Offline && !it.loading
        }
        receiver.sessions.onSuccess()
        // The state is Online and not loading before the reload starts, so the reload is
        // awaited by its request.
        receiver.awaitRequests(2) {
            it.requestUrl?.encodedPath == BOUQUET_INDEX_PATH && it.isBouquetIndex()
        }
        awaitLoaded(viewModel)
        assertEquals(2, receiver.requestsTo(BOUQUET_INDEX_PATH).count { it.isBouquetIndex() })
    }

    @Test
    fun reloadDropsAVanishedBouquetAndKeepsTimers() = runBlocking<Unit> {
        val viewModel = viewModel()
        val opened = awaitLoaded(viewModel)
        assertEquals(HubSection.Live(FAVOURITES), opened.section)

        // Each wait names the reloaded rows: a bare "loaded" also matches the state
        // before the reload reached the combined uiState.
        bouquets = listOf(OTHER to "Other")
        viewModel.reload()
        val dropped = awaitState(viewModel) { !it.loading && it.rowRefs() == listOf(OTHER) }
        assertEquals(HubSection.Live(OTHER), dropped.section)

        viewModel.openTab(HubTab.Timers)
        awaitState(viewModel) { it.section == HubSection.Timers }
        bouquets = listOf(FAVOURITES to "Favourites")
        viewModel.reload()
        val kept = awaitState(viewModel) { !it.loading && it.rowRefs() == listOf(FAVOURITES) }
        assertEquals(HubSection.Timers, kept.section)
    }

    @Test
    fun backReturnsToLiveTvOnItsBouquet() = runBlocking<Unit> {
        bouquets = listOf(FAVOURITES to "Favourites", OTHER to "Other")
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        viewModel.selectDrawerItem(OTHER)
        viewModel.openTab(HubTab.Guide)
        awaitState(viewModel) { it.section == HubSection.Guide(OTHER) }

        assertTrue(viewModel.back())
        awaitState(viewModel) { it.section == HubSection.Live(OTHER) }
        assertFalse(viewModel.back())
    }

    @Test
    fun theActiveProfileNamesTheChip() = runBlocking<Unit> {
        val name = receiver.profiles.repository.current.value!!.name.orEmpty()

        awaitState(viewModel()) { it.profileName == name && name.isNotEmpty() }
    }

    @Test
    fun overlayAndEditorOpenAndClose() = runBlocking<Unit> {
        val viewModel = viewModel()
        val service = ServiceNowNext(serviceReference = CHANNEL, serviceName = "Das Erste HD")
        val event = Event(eventId = "42", title = "News")
        val target = TvServiceTimerTarget(service, FAVOURITES)

        // uiState is combined on the thread that last emitted (Room's for the cache), so
        // each change is awaited rather than read from value.
        viewModel.showServiceTimer(service, FAVOURITES)
        viewModel.showEditTimer(event)
        awaitState(viewModel) { it.serviceTimerTarget == target && it.editTimerEvent == event }

        viewModel.dismissEditTimer()
        awaitState(viewModel) { it.serviceTimerTarget == target && it.editTimerEvent == null }

        viewModel.showEditTimer(event)
        viewModel.onTimerSaved()
        awaitState(viewModel) { it.serviceTimerTarget == null && it.editTimerEvent == null }
    }

    @Test
    fun setTimerReportsTheReceiverAnswerAndClosesTheOverlay() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.showServiceTimer(
            ServiceNowNext(serviceReference = CHANNEL, serviceName = "Das Erste HD"),
            FAVOURITES
        )

        viewModel.setTimer(Event(eventId = "42", serviceReference = CHANNEL))
        val state = awaitState(viewModel) { it.userMessage != null }

        assertEquals(UiText.Raw("Timer added"), state.userMessage)
        assertNull(state.serviceTimerTarget)
        assertFalse(state.settingTimer)
        val request = receiver.requestsTo("/web/timeraddbyeventid").single()
        assertEquals("42", request.requestUrl!!.queryParameter("eventid"))
        viewModel.onMessageShown()
        // uiState is combined; the cleared message arrives asynchronously.
        awaitState(viewModel) { it.userMessage == null }
    }

    @Test
    fun streamServiceHandsTheStreamToTheHost() = runBlocking<Unit> {
        val viewModel = viewModel()
        val service = ServiceNowNext(serviceReference = CHANNEL, serviceName = "Das Erste HD")

        viewModel.streamService(service, FAVOURITES)
        val stream = awaitState(viewModel) { it.stream != null }.stream

        val profile = receiver.profiles.repository.current.value!!
        assertEquals(
            TvStreamOpen.Service(
                service,
                FAVOURITES,
                LiveStream.Ready(CHANNEL, EnigmaUrls.stream(profile, CHANNEL))
            ),
            stream
        )
        assertTrue(receiver.requestsTo(ZAP).isEmpty())
        viewModel.onStreamStarted()
        awaitState(viewModel) { it.stream == null }
    }

    @Test
    fun continueWatchingFollowsThePlayerAndTheActiveProfile() = runBlocking<Unit> {
        val viewModel = viewModel()
        assertNull(viewModel.uiState.value.continueWatching)
        val zdf = LastLiveStream(CHANNEL, "Das Erste HD", FAVOURITES)

        lastStreams.save(zdf)
        awaitState(viewModel) { it.continueWatching?.service?.serviceReference == CHANNEL }

        val active = receiver.profiles.repository.current.value!!
        receiver.profiles.repository.setCurrent(Profile().apply { id = PROFILE_ID + 1 })
        awaitState(viewModel) { it.continueWatching == null }

        receiver.profiles.repository.setCurrent(active)
        awaitState(viewModel) { it.continueWatching?.bouquetRef == FAVOURITES }
    }

    /** The card shows the channel as the loaded bouquets have it: name and programme. */
    @Test
    fun continueWatchingTakesTheChannelFromTheLoadedBouquets() {
        val now = Event(title = "heute journal")
        val zdf = ServiceNowNext(serviceReference = CHANNEL, serviceName = "ZDF HD", now = now)
        val rows = listOf(
            HubBouquetRow(Service("other", "Other"), listOf(zdf.copy(now = null))),
            HubBouquetRow(Service(FAVOURITES, "Favourites"), listOf(zdf))
        )

        assertEquals(
            ContinueWatching(zdf, FAVOURITES),
            continueWatching(LastLiveStream(CHANNEL, "", FAVOURITES), rows)
        )
        assertEquals(
            ContinueWatching(ServiceNowNext(serviceReference = "gone", serviceName = "Old"), null),
            continueWatching(LastLiveStream("gone", "Old", null), rows)
        )
    }

    @Test
    fun rejectedZapAndStreamShowsTheReceiverText() = runBlocking<Unit> {
        receiver.profiles.repository.current.value!!.zapAndStream = true
        val viewModel = viewModel()

        viewModel.streamService(
            ServiceNowNext(serviceReference = CHANNEL, serviceName = "Das Erste HD"),
            FAVOURITES
        )
        val state = awaitState(viewModel) { it.userMessage != null }

        assertEquals(UiText.Raw("No free tuner"), state.userMessage)
        assertNull(state.stream)
        assertEquals(CHANNEL, receiver.requestsTo(ZAP).single().requestUrl!!.queryParameter("sRef"))
    }

    @Test
    fun streamMovieHandsTheRecordingUrlToTheHost() = runBlocking<Unit> {
        val viewModel = viewModel()
        val movie = Movie(reference = MOVIE_REF, fileName = MOVIE_FILE, title = "News")

        viewModel.streamMovie(movie)
        val stream = awaitState(viewModel) { it.stream != null }.stream

        val profile = receiver.profiles.repository.current.value!!
        assertEquals(
            TvStreamOpen.Recording(movie, EnigmaUrls.fileStream(profile, MOVIE_REF, MOVIE_FILE)),
            stream
        )
    }

    @Test
    fun streamMovieWithoutAnActiveProfileSaysWhy() = runBlocking<Unit> {
        val viewModel = viewModel()
        receiver.profiles.repository.clearCurrent()
        val movie = Movie(reference = MOVIE_REF, fileName = MOVIE_FILE, title = "News")

        viewModel.streamMovie(movie)

        val state = awaitState(viewModel) { it.userMessage != null }
        assertEquals(UiText.Resource(R.string.no_profile_body), state.userMessage)
        assertNull(state.stream)
    }

    @Test
    fun missingPlayerIsAUserMessage() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onMissingStreamPlayer()

        awaitState(viewModel) { it.userMessage == UiText.Resource(R.string.missing_stream_player) }
    }

    private fun routes(request: RecordedRequest): MockResponse {
        if (receiverDown) {
            return MockResponse().setResponseCode(500)
        }
        return when (request.requestUrl?.encodedPath) {
            BOUQUET_INDEX_PATH -> MockResponse().setBody(
                if (request.isBouquetIndex()) {
                    serviceList(bouquets)
                } else {
                    loadWebFixture("getservices.xml")
                }
            )

            "/web/epgnownext" -> MockResponse().setBody(loadWebFixture("epgnownext.xml"))

            EPG_MULTI -> MockResponse().setBody(
                if (epgInEveryWindow) {
                    loadWebFixture("epgmulti.xml")
                        .replace("1893456000", (request.time() + 600L).toString())
                } else {
                    loadWebFixture("epgmulti.xml")
                }
            )

            "/web/getlocations" -> MockResponse().setBody(
                "<e2locations><e2location>$HDD</e2location></e2locations>"
            )

            "/web/gettags" -> MockResponse().setBody("<e2tags><e2tag>News</e2tag></e2tags>")

            MOVIE_LIST -> MockResponse().setBody(loadWebFixture("movielist.xml"))

            "/web/timeraddbyeventid" -> MockResponse().setBody(
                "<e2simplexmlresult><e2state>True</e2state>" +
                    "<e2statetext>Timer added</e2statetext></e2simplexmlresult>"
            )

            ZAP -> MockResponse().setBody(simpleResult(false, "No free tuner"))

            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun viewModel(epgSyncDelayMs: Long = TvHubViewModel.EPG_SYNC_DELAY_MS): TvHubViewModel =
        TvHubViewModel(
            TvHubBrowse(
                receiver.services,
                receiver.repository,
                movies,
                timers,
                receiver.profiles.repository,
                receiver.sessions
            ),
            timers,
            ReceiverRepository(clients, receiver.profiles.repository),
            movies,
            receiver.sessions,
            receiver.profiles.repository,
            lastStreams,
            epgSyncDelayMs
        ).also { viewModels += it }

    private suspend fun awaitLoaded(viewModel: TvHubViewModel): TvHubUiState =
        awaitState(viewModel) { !it.loading }

    private suspend fun awaitState(
        viewModel: TvHubViewModel,
        condition: (TvHubUiState) -> Boolean
    ): TvHubUiState = withTimeout(5_000L) { viewModel.uiState.first(condition) }

    private fun RecordedRequest.time(): Long = requestUrl!!.queryParameter("time")!!.toLong()

    private fun RecordedRequest.bRef(): String? = requestUrl?.queryParameter("bRef")

    private fun TvHubUiState.rowRefs(): List<String> = bouquetRows.map { it.bouquet.reference }

    private fun RecordedRequest.isBouquetIndex(): Boolean =
        requestUrl?.queryParameter("sRef") == TV_ROOTS[0]

    private fun RecordedRequest.dirname(): String? = requestUrl?.queryParameter("dirname")

    private fun serviceList(services: List<Pair<String, String>>): String =
        services.joinToString("", "<e2servicelist>", "</e2servicelist>") { (ref, name) ->
            "<e2service><e2servicereference>$ref</e2servicereference>" +
                "<e2servicename>$name</e2servicename></e2service>"
        }

    companion object {
        const val FAVOURITES =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        const val OTHER =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
        const val CHANNEL = "1:0:1:6DCA:44D:1:C00000:0:0:0:"
        const val MARKER = "1:64:1:0:0:0:0:0:0:0:"
        const val HDD = "/media/hdd/movie/"
        const val BOUQUET_INDEX_PATH = "/web/getservices"
        const val EPG_MULTI = "/web/epgmulti"
        const val MOVIE_LIST = "/web/movielist"
        const val ZAP = "/web/zap"
        const val MOVIE_FILE = "/media/hdd/movie/news.ts"
        const val MOVIE_REF = "1:0:0:0:0:0:0:0:0:0:$MOVIE_FILE"
    }
}
