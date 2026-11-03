package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ReceiverPluginsRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.MultiEpgZoom
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.BOUQUET
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.jobs
import net.reichholf.dreamdroid.testutil.joinJobsSince
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.receiverApis
import net.reichholf.dreamdroid.ui.multiepg.MULTI_EPG_VISIBLE_MINUTES_KEY
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

/** [TvMultiEpgViewModel] over the real repositories, a MockWebServer receiver, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvMultiEpgViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<TvMultiEpgViewModel>()

    /** The `/web/epgmulti` answer; null answers the fixture. */
    @Volatile
    private var epgMultiBody: String? = null

    /** The `/web/getservices` answer; null answers the fixture. */
    @Volatile
    private var tvBouquetsBody: String? = null

    /** When set, `/web/getservices` waits for it before answering. */
    @Volatile
    private var tvBouquetsGate: CountDownLatch? = null

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = ::routes
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        // A test that failed before opening the gate must not leave the dispatcher blocked.
        tvBouquetsGate?.countDown()
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun openLoadsTheLaunchBouquetAndTheBouquetList() = runBlocking {
        val viewModel = viewModel()

        viewModel.open(BOUQUET, "Favourites")
        val state = awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        assertEquals(BOUQUET, state.bouquetRef)
        assertEquals(UiText.Raw("Favourites"), state.title)
        assertTrue(state.bouquets.any { it.name == "Das Erste HD" })
        assertTrue(receiver.requests.any { it.requestUrl?.encodedPath == "/web/epgmulti" })
    }

    @Test
    fun programmesTheHubFilledShowInTheGrid() = runBlocking<Unit> {
        val now = System.currentTimeMillis() / 1000L
        epgMultiBody = epgMultiAround(now)
        // The hub writes the tab strip and fills the chunk at now before MultiEPG opens.
        receiver.services.tvBouquetTabs()
        receiver.repository.fillNowChunk(BOUQUET, BOUQUET, now)
        val viewModel = viewModel()

        viewModel.open(BOUQUET, "Favourites")
        val state = awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        val bar = state.grid.channels.single { it.serviceRef == BOUQUET_CHANNEL }.bars.single()
        assertEquals("Now", bar.event.title)
        assertTrue(now in bar.startSec until bar.endSec)
    }

    @Test
    fun openWithoutABouquetOpensTheFirstOne() = runBlocking {
        val viewModel = viewModel()

        viewModel.open(null, null)
        val state = awaitState(viewModel) { it.bouquetRef.isNotEmpty() }

        assertEquals(state.bouquets.first().reference, state.bouquetRef)
    }

    @Test
    fun noActiveProfileLoadsNoBouquets() = runBlocking {
        val viewModel = viewModel()
        receiver.profiles.repository.clearCurrent()
        val before = viewModel.jobs()

        viewModel.open(BOUQUET, "Favourites")
        viewModel.joinJobsSince(before)

        assertTrue(viewModel.uiState.value.bouquets.isEmpty())
        assertTrue(receiver.requests.isEmpty())
    }

    @Test
    fun titleFallsBackToMultiEpg() {
        assertEquals(UiText.Resource(R.string.multiepg), viewModel().uiState.value.title)
    }

    @Test
    fun setTimerReportsTheReceiverAnswer() = runBlocking {
        val viewModel = viewModel()

        viewModel.setTimer(Event(eventId = "42", serviceReference = BOUQUET_CHANNEL))
        val state = awaitState(viewModel) { it.userMessage != null }

        assertEquals(UiText.Raw("Timer added"), state.userMessage)
        assertFalse(state.settingTimer)
        val request = receiver.requests.single {
            it.requestUrl?.encodedPath == "/web/timeraddbyeventid"
        }
        assertEquals("42", request.requestUrl!!.queryParameter("eventid"))
        viewModel.onMessageShown()
        // uiState is combined on the thread that last emitted, here the HTTP answer's.
        awaitState(viewModel) { it.userMessage == null }
    }

    @Test
    fun streamHandsTheEventServiceToTheHost() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.open(BOUQUET, "Favourites")
        awaitState(viewModel) { it.bouquetRef == BOUQUET }

        viewModel.stream(
            Event(title = "News", serviceReference = BOUQUET_CHANNEL, serviceName = "Das Erste HD")
        )
        val stream = awaitState(viewModel) { it.stream != null }.stream

        val profile = receiver.profiles.repository.current.value!!
        assertEquals(
            TvMultiEpgStream(
                "News",
                ServiceNowNext(serviceReference = BOUQUET_CHANNEL, serviceName = "Das Erste HD"),
                BOUQUET,
                LiveStream.Ready(BOUQUET_CHANNEL, EnigmaUrls.stream(profile, BOUQUET_CHANNEL))
            ),
            stream
        )
        viewModel.onStreamStarted()
        awaitState(viewModel) { it.stream == null }
    }

    @Test
    fun rejectedZapAndStreamShowsTheReceiverText() = runBlocking<Unit> {
        receiver.profiles.repository.current.value!!.zapAndStream = true
        val viewModel = viewModel()

        viewModel.stream(
            Event(title = "News", serviceReference = BOUQUET_CHANNEL, serviceName = "Das Erste HD")
        )
        val state = awaitState(viewModel) { it.userMessage != null }

        assertEquals(UiText.Raw("No free tuner"), state.userMessage)
        assertNull(state.stream)
    }

    @Test
    fun sessionDecidesStreamingAndMutations() = runBlocking {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.streamingEnabled)
        assertFalse(viewModel.uiState.value.mutationsBlocked)

        receiver.goOffline()
        val state = awaitState(viewModel) { it.mutationsBlocked }

        assertFalse(state.streamingEnabled)
    }

    @Test
    fun overlaysTakeTheGridKeys() = runBlocking {
        val event = Event(eventId = "42", title = "News")
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.gridKeysEnabled)

        viewModel.showDetail(event)
        assertEquals(event, viewModel.uiState.value.detailEvent)
        assertFalse(viewModel.uiState.value.gridKeysEnabled)

        viewModel.editTimer(event)
        assertNull(viewModel.uiState.value.detailEvent)
        assertEquals(event, viewModel.uiState.value.editTimerEvent)

        viewModel.dismissTimerEditor()
        assertNull(viewModel.uiState.value.editTimerEvent)
        assertTrue(viewModel.uiState.value.gridKeysEnabled)

        viewModel.open(BOUQUET, "Favourites")
        awaitState(viewModel) { it.bouquets.isNotEmpty() }
        viewModel.showBouquetPicker()
        val picked = awaitState(viewModel) { it.pickingBouquet }
        assertFalse(picked.gridKeysEnabled)
        viewModel.pickBouquet(Service(viewModel.uiState.value.bouquetRef, "Same"))
        val dismissed = awaitState(viewModel) { !it.pickingBouquet }
        assertTrue(dismissed.gridKeysEnabled)
    }

    @Test
    fun bouquetPickerStaysClosedWithoutBouquets() {
        val viewModel = viewModel()

        viewModel.showBouquetPicker()

        assertFalse(viewModel.uiState.value.pickingBouquet)
        assertTrue(viewModel.uiState.value.gridKeysEnabled)
    }

    @Test
    fun pickingAnotherBouquetLoadsIt() = runBlocking {
        val viewModel = viewModel()
        viewModel.open(BOUQUET, "Favourites")
        awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        viewModel.pickBouquet(Service(OTHER_BOUQUET, "Other"))
        val state = awaitState(viewModel) {
            it.grid.bouquetRef == OTHER_BOUQUET && it.grid.channels.isNotEmpty() &&
                !it.grid.syncing
        }

        assertEquals(UiText.Raw("Other"), state.title)
        assertTrue(
            receiver.requests.any { it.requestUrl?.queryParameter("bRef") == OTHER_BOUQUET }
        )
    }

    @Test
    fun openingAgainSwitchesToThatBouquet() = runBlocking {
        val viewModel = viewModel()
        viewModel.open(BOUQUET, "Favourites")
        awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        viewModel.open(OTHER_BOUQUET, "Other")
        val state = awaitState(viewModel) {
            it.grid.bouquetRef == OTHER_BOUQUET && it.grid.channels.isNotEmpty() &&
                !it.grid.syncing
        }

        assertEquals(OTHER_BOUQUET, state.bouquetRef)
        assertEquals(UiText.Raw("Other"), state.title)
    }

    @Test
    fun profileSwitchReloadsTheBouquetForTheNewProfile() = runBlocking<Unit> {
        epgMultiBody = epgMultiAround(System.currentTimeMillis() / 1000L)
        val viewModel = viewModel()
        viewModel.onShown()
        viewModel.open(BOUQUET, "Favourites")
        awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }
        val requestsBefore = epgMultiTimes().size

        // Hold the new profile's bouquet list so the invalidation is observable on its own.
        val bouquetsGate = CountDownLatch(1)
        tvBouquetsGate = bouquetsGate
        // The new profile's receiver answers a programme the old grid cannot show.
        epgMultiBody = epgMultiAround(System.currentTimeMillis() / 1000L, title = "Switched")
        // A different default proves the Guide kept the hub's bouquet, not the default.
        switchToAnotherProfile(defaultBouquetRef = OTHER_BOUQUET)

        val invalidated = awaitState(viewModel) { it.grid.channels.isEmpty() }
        assertTrue(invalidated.grid.bouquetRef.isEmpty())

        bouquetsGate.countDown()
        val state = awaitState(viewModel) {
            it.grid.channels.any { channel ->
                channel.bars.any { bar -> bar.event.title == "Switched" }
            } && !it.grid.syncing
        }

        assertEquals(BOUQUET, state.bouquetRef)
        assertEquals(BOUQUET, state.grid.bouquetRef)
        assertEquals(UiText.Raw("Favourites"), state.title)
        assertTrue(
            epgMultiTimes().size > requestsBefore,
            "the switch must refetch the bouquet for the new profile"
        )
    }

    @Test
    fun profileSwitchWithoutTheBouquetWaitsForTheHub() = runBlocking<Unit> {
        epgMultiBody = epgMultiAround(System.currentTimeMillis() / 1000L)
        val viewModel = viewModel()
        viewModel.onShown()
        viewModel.open(BOUQUET, "Favourites")
        awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }
        val requestsBefore = epgMultiTimes().size

        tvBouquetsBody = serviceList(OTHER_BOUQUET, "Other")
        // The profile default is the old bouquet, which this profile does not list: only the
        // hub's fallback may load, never the default.
        val other = switchToAnotherProfile(defaultBouquetRef = BOUQUET)

        // The Guide picks no fallback of its own: it drops the old grid and waits.
        val waiting = awaitState(viewModel) {
            it.grid.channels.isEmpty() && it.bouquetRef.isEmpty()
        }
        assertEquals(listOf(OTHER_BOUQUET), waiting.bouquets.map { it.reference })
        assertEquals(requestsBefore, epgMultiTimes().size, "the Guide must start no load here")

        // The hub reconciles to the new profile's only bouquet and hands it over: one load.
        viewModel.open(OTHER_BOUQUET, "Other")
        val state = awaitState(viewModel) {
            it.grid.bouquetRef == OTHER_BOUQUET && it.grid.channels.isNotEmpty() && !it.grid.syncing
        }

        assertEquals(UiText.Raw("Other"), state.title)
        val refsAfterSwitch = epgMultiBouquets().drop(requestsBefore)
        assertTrue(refsAfterSwitch.isNotEmpty(), "the hub's bouquet must load")
        assertTrue(
            refsAfterSwitch.all { it == OTHER_BOUQUET },
            "only the hub's bouquet loads, got $refsAfterSwitch"
        )
        // The gate was rebuilt from the new profile's tabs: the hub's bouquet caches.
        val chunk = MultiEpgWindows.chunkContaining(state.grid.anchorSec)
        assertNotNull(
            receiver.profiles.database.epgDao()
                .getChunk(other.id!!, OTHER_BOUQUET, chunk.startSec)
        )
    }

    @Test
    fun aProfileSwitchWhileHiddenLoadsNothing() = runBlocking<Unit> {
        epgMultiBody = epgMultiAround(System.currentTimeMillis() / 1000L)
        val viewModel = viewModel()
        viewModel.onShown()
        val collectors = viewModel.jobs()
        viewModel.open(BOUQUET, "Favourites")
        viewModel.joinJobsSince(collectors)
        val painted = awaitState(viewModel) {
            it.grid.channels.isNotEmpty() && !it.grid.syncing
        }
        val requestsBefore = epgMultiTimes().size

        viewModel.onHidden()
        // Under the unconfined main dispatcher the switch's collector runs before this
        // returns, so an unchanged job set and request list mean nothing started.
        epgMultiBody = epgMultiAround(System.currentTimeMillis() / 1000L, title = "Switched")
        switchToAnotherProfile()

        assertEquals(collectors, viewModel.jobs())
        assertEquals(requestsBefore, epgMultiTimes().size)
        assertEquals(painted.grid.channels, viewModel.uiState.value.grid.channels)

        // Back on screen, the hub's bouquet reloads under the new profile, once.
        viewModel.onShown()
        val reloaded = awaitState(viewModel) {
            it.grid.channels.any { channel ->
                channel.bars.any { bar -> bar.event.title == "Switched" }
            } && !it.grid.syncing
        }

        assertEquals(BOUQUET, reloaded.grid.bouquetRef)
        assertEquals(UiText.Raw("Favourites"), reloaded.title)
        val afterShow = epgMultiBouquets().drop(requestsBefore)
        assertTrue(afterShow.isNotEmpty(), "the deferred reload must fetch")
        assertTrue(afterShow.all { it == BOUQUET }, "only the shown bouquet loads, got $afterShow")
    }

    @Test
    fun refreshRefetchesThePannedChunk() = runBlocking {
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                "/web/epgmulti" -> MockResponse().setBody(
                    epgMultiAround(request.requestUrl!!.queryParameter("time")!!.toLong())
                )

                else -> routes(request)
            }
        }
        val viewModel = viewModel()
        viewModel.open(BOUQUET, "Favourites")
        val loaded = awaitState(viewModel) {
            it.grid.channels.isNotEmpty() && !it.grid.syncing
        }
        val day3 = MultiEpgWindows.chunkContaining(loaded.grid.anchorSec).startSec +
            2L * MultiEpgWindows.CHUNK_SECONDS
        val viewStart = day3 + 3_600L

        viewModel.onVisibleWindow(viewStart, viewStart + 7_200L)
        awaitState(viewModel) {
            it.grid.timelineStartSec == day3 && !it.grid.syncing
        }
        val before = epgMultiTimes().size

        viewModel.refresh()
        // The warm behind the pan also adds a request; wait for the refresh's own day3 fetch.
        awaitState(viewModel) {
            !it.grid.syncing && epgMultiTimes().drop(before).contains(day3.toString())
        }

        val refreshed = epgMultiTimes().drop(before)
        assertTrue(
            day3.toString() in refreshed,
            "refresh must refetch the panned chunk $day3, got $refreshed"
        )
        assertEquals(day3, viewModel.uiState.value.grid.timelineStartSec)
    }

    @Test
    fun visibleMinutesRestoreFromAndWriteToSavedState() {
        val handle = SavedStateHandle(mapOf(MULTI_EPG_VISIBLE_MINUTES_KEY to 240))
        val viewModel = viewModel(handle)
        assertEquals(240, viewModel.uiState.value.visibleMinutes)

        viewModel.onVisibleMinutesChange(60)

        assertEquals(60, handle.get<Int>(MULTI_EPG_VISIBLE_MINUTES_KEY))
        assertEquals(MultiEpgZoom.DEFAULT_MINUTES, viewModel().uiState.value.visibleMinutes)
    }

    /** Activates a second profile on the same receiver, as the TV profile screen does. */
    private suspend fun switchToAnotherProfile(defaultBouquetRef: String = BOUQUET): Profile {
        val first = receiver.profiles.repository.current.value!!
        val other = Profile().apply {
            name = "other"
            host = first.host
            port = first.port
            defaultBouquetTv = defaultBouquetRef
            defaultBouquetTvName = "Other"
        }
        receiver.profiles.repository.save(other)
        receiver.profiles.repository.setCurrent(other.id!!, forceEvent = true)
        return other
    }

    /** The `time` of each `/web/epgmulti` request, in order. */
    private fun epgMultiTimes(): List<String?> = receiver.requests
        .filter { it.requestUrl?.encodedPath == "/web/epgmulti" }
        .map { it.requestUrl?.queryParameter("time") }

    /** The `bRef` of each `/web/epgmulti` request, in order. */
    private fun epgMultiBouquets(): List<String?> = receiver.requests
        .filter { it.requestUrl?.encodedPath == "/web/epgmulti" }
        .map { it.requestUrl?.queryParameter("bRef") }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.encodedPath) {
            "/web/epgmulti" -> MockResponse().setBody(
                epgMultiBody ?: loadWebFixture("epgmulti.xml")
            )

            "/web/getservices" -> {
                tvBouquetsGate?.await()
                MockResponse().setBody(tvBouquetsBody ?: loadWebFixture("getservices.xml"))
            }

            "/web/timerlist" -> MockResponse().setBody(loadWebFixture("timerlist.xml"))

            "/web/timeraddbyeventid" -> MockResponse().setBody(
                "<e2simplexmlresult><e2state>True</e2state>" +
                    "<e2statetext>Timer added</e2statetext></e2simplexmlresult>"
            )

            "/web/zap" -> MockResponse().setBody(simpleResult(false, "No free tuner"))

            else -> MockResponse().setResponseCode(404)
        }

    /** One programme on [BOUQUET_CHANNEL] that started ten minutes before [nowSec]. */
    private fun epgMultiAround(nowSec: Long, title: String = "Now"): String =
        "<e2eventlist><e2event><e2eventid>1</e2eventid>" +
            "<e2eventstart>${nowSec - 600}</e2eventstart>" +
            "<e2eventduration>3600</e2eventduration>" +
            "<e2eventcurrenttime>$nowSec</e2eventcurrenttime>" +
            "<e2eventtitle>$title</e2eventtitle>" +
            "<e2eventservicereference>$BOUQUET_CHANNEL</e2eventservicereference>" +
            "<e2eventservicename>Das Erste HD</e2eventservicename>" +
            "</e2event></e2eventlist>"

    /** A `/web/getservices` answer with the single user bouquet [ref]. */
    private fun serviceList(ref: String, name: String): String = "<e2servicelist><e2service>" +
        "<e2servicereference>$ref</e2servicereference>" +
        "<e2servicename>$name</e2servicename>" +
        "</e2service></e2servicelist>"

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): TvMultiEpgViewModel =
        TvMultiEpgViewModel(
            handle,
            receiver.repository,
            receiver.services,
            TimerRepository(
                receiverApis(receiver.profiles.repository),
                receiver.profiles.repository,
                receiver.profiles.database,
                ReceiverPluginsRepository(
                    receiverApis(receiver.profiles.repository),
                    receiver.profiles.repository
                ),
                receiver.sessions
            ),
            receiver.profiles.repository,
            ReceiverRepository(
                receiverApis(receiver.profiles.repository),
                receiver.profiles.repository
            ),
            receiver.sessions,
            SettingsRepository(MemorySharedPreferences())
        ).also { viewModels += it }

    private suspend fun awaitState(
        viewModel: TvMultiEpgViewModel,
        condition: (TvMultiEpgUiState) -> Boolean
    ): TvMultiEpgUiState = withTimeout(5_000L) { viewModel.uiState.first(condition) }

    companion object {
        const val BOUQUET_CHANNEL = "1:0:1:6DCA:44D:1:C00000:0:0:0:"
        const val OTHER_BOUQUET =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\" ORDER BY bouquet"
    }
}
