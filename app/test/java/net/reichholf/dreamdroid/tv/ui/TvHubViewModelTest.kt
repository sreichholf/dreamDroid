package net.reichholf.dreamdroid.tv.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ReceiverPluginsRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.PROFILE_ID
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
    private val movies = MovieRepository(
        receiver.profiles.context,
        clients,
        receiver.profiles.repository,
        receiver.profiles.database
    )
    private val timers = TimerRepository(
        clients,
        receiver.profiles.repository,
        receiver.profiles.database,
        ReceiverPluginsRepository(clients, receiver.profiles.repository)
    )
    private val viewModels = mutableListOf<TvHubViewModel>()

    /** The TV bouquet index the receiver answers; empty means none. */
    @Volatile
    private var bouquets = listOf(FAVOURITES to "Favourites")

    @Volatile
    private var receiverDown = false

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
        awaitLoaded(viewModel())
        receiverDown = true

        val state = awaitLoaded(viewModel())

        assertEquals(listOf(FAVOURITES), state.bouquetRows.map { it.bouquet.reference })
        assertNull(state.errorText)
    }

    @Test
    fun aMovieHeaderLoadsItsLocationOnce() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        viewModel.selectHeader(MOVIE_HEADER)
        awaitState(viewModel) { HDD in it.moviesByLocation }
        viewModel.selectHeader(FAVOURITES)
        viewModel.selectHeader(MOVIE_HEADER)
        val state = awaitState(viewModel) { !it.movieLoading }

        assertEquals(2, state.moviesByLocation.getValue(HDD).size)
        assertEquals(1, receiver.requestsTo(MOVIE_LIST).size)
        assertEquals(listOf(HDD), receiver.requestsTo(MOVIE_LIST).map { it.dirname() })
    }

    @Test
    fun offlineMoviesComeFromRoom() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        viewModel.selectHeader(MOVIE_HEADER)
        awaitState(viewModel) { HDD in it.moviesByLocation }

        receiver.goOffline()
        val state = awaitState(viewModel) {
            it.connection.session == ConnectionStatus.Session.Offline &&
                !it.loading && HDD in it.moviesByLocation
        }

        assertEquals(MOVIE_HEADER, state.selectedHeaderId)
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
    fun reloadDropsAVanishedHeaderAndKeepsPersistentOnes() = runBlocking<Unit> {
        val viewModel = viewModel()
        awaitLoaded(viewModel)
        viewModel.selectHeader(FAVOURITES)
        awaitState(viewModel) { it.selectedHeaderId == FAVOURITES }

        // Each wait names the reloaded rows: a bare "loaded" also matches the state
        // before the reload reached the combined uiState.
        bouquets = listOf(OTHER to "Other")
        viewModel.reload()
        val dropped = awaitState(viewModel) { !it.loading && it.rowRefs() == listOf(OTHER) }
        assertEquals(TvComposeHubHost.HEADER_SETTINGS_ID, dropped.selectedHeaderId)

        viewModel.selectHeader(TvComposeHubHost.HEADER_TIMERS_ID)
        awaitState(viewModel) { it.selectedHeaderId == TvComposeHubHost.HEADER_TIMERS_ID }
        bouquets = listOf(FAVOURITES to "Favourites")
        viewModel.reload()
        val kept = awaitState(viewModel) { !it.loading && it.rowRefs() == listOf(FAVOURITES) }
        assertEquals(TvComposeHubHost.HEADER_TIMERS_ID, kept.selectedHeaderId)
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

        val profile = receiver.profiles.repository.requireCurrent()
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
    fun rejectedZapAndStreamShowsTheReceiverText() = runBlocking<Unit> {
        receiver.profiles.repository.requireCurrent().zapAndStream = true
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

        val profile = receiver.profiles.repository.requireCurrent()
        assertEquals(
            TvStreamOpen.Recording(movie, EnigmaUrls.fileStream(profile, MOVIE_REF, MOVIE_FILE)),
            stream
        )
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

            EPG_MULTI -> MockResponse().setBody(loadWebFixture("epgmulti.xml"))

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

    private fun viewModel(): TvHubViewModel = TvHubViewModel(
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
        receiver.sessions
    ).also { viewModels += it }

    private suspend fun awaitLoaded(viewModel: TvHubViewModel): TvHubUiState =
        awaitState(viewModel) { !it.loading }

    private suspend fun awaitState(
        viewModel: TvHubViewModel,
        condition: (TvHubUiState) -> Boolean
    ): TvHubUiState = withTimeout(5_000L) { viewModel.uiState.first(condition) }

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
        val MOVIE_HEADER = TvComposeHubHost.movieHeaderId(HDD)
        const val BOUQUET_INDEX_PATH = "/web/getservices"
        const val EPG_MULTI = "/web/epgmulti"
        const val MOVIE_LIST = "/web/movielist"
        const val ZAP = "/web/zap"
        const val MOVIE_FILE = "/media/hdd/movie/news.ts"
        const val MOVIE_REF = "1:0:0:0:0:0:0:0:0:0:$MOVIE_FILE"
    }
}
