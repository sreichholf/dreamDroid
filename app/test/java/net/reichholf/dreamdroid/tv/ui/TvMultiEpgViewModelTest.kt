package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgZoom
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.EpgTestReceiver.Companion.BOUQUET
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.multiepg.MULTI_EPG_VISIBLE_MINUTES_KEY
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [TvMultiEpgViewModel] over the real repositories, a MockWebServer receiver, and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
class TvMultiEpgViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<TvMultiEpgViewModel>()

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
    fun startLoadsTheLaunchBouquetAndTheBouquetList() = runBlocking {
        val viewModel = viewModel()

        viewModel.start(BOUQUET, "Favourites")
        val state = awaitState(viewModel) { it.grid.channels.isNotEmpty() && !it.grid.syncing }

        assertEquals(BOUQUET, state.bouquetRef)
        assertEquals(UiText.Raw("Favourites"), state.title)
        assertTrue(state.bouquets.any { it.name == "Das Erste HD" })
        assertTrue(receiver.requests.any { it.requestUrl?.encodedPath == "/web/epgmulti" })
    }

    @Test
    fun startWithoutExtraOpensTheFirstBouquet() = runBlocking {
        val viewModel = viewModel()

        viewModel.start(null, null)
        val state = awaitState(viewModel) { it.bouquetRef.isNotEmpty() }

        assertEquals(state.bouquets.first().reference, state.bouquetRef)
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
    fun sessionDecidesStreamingAndMutations() = runBlocking {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.streamingEnabled)
        assertFalse(viewModel.uiState.value.mutationsBlocked)

        receiver.goOffline()
        val state = awaitState(viewModel) { it.mutationsBlocked }

        assertFalse(state.streamingEnabled)
    }

    @Test
    fun overlaysTakeTheGridKeys() {
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

        viewModel.showBouquetPicker()
        assertTrue(viewModel.uiState.value.pickingBouquet)
        viewModel.pickBouquet(Service(viewModel.uiState.value.bouquetRef, "Same"))
        assertFalse(viewModel.uiState.value.pickingBouquet)
    }

    @Test
    fun pickingAnotherBouquetLoadsIt() = runBlocking {
        val viewModel = viewModel()
        viewModel.start(BOUQUET, "Favourites")
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
    fun visibleMinutesRestoreFromAndWriteToSavedState() {
        val handle = SavedStateHandle(mapOf(MULTI_EPG_VISIBLE_MINUTES_KEY to 240))
        val viewModel = viewModel(handle)
        assertEquals(240, viewModel.uiState.value.visibleMinutes)

        viewModel.onVisibleMinutesChange(60)

        assertEquals(60, handle.get<Int>(MULTI_EPG_VISIBLE_MINUTES_KEY))
        assertEquals(MultiEpgZoom.DEFAULT_MINUTES, viewModel().uiState.value.visibleMinutes)
    }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.encodedPath) {
            "/web/epgmulti" -> MockResponse().setBody(loadWebFixture("epgmulti.xml"))

            "/web/getservices" -> MockResponse().setBody(loadWebFixture("getservices.xml"))

            "/web/timerlist" -> MockResponse().setBody(loadWebFixture("timerlist.xml"))

            "/web/timeraddbyeventid" -> MockResponse().setBody(
                "<e2simplexmlresult><e2state>True</e2state>" +
                    "<e2statetext>Timer added</e2statetext></e2simplexmlresult>"
            )

            else -> MockResponse().setResponseCode(404)
        }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): TvMultiEpgViewModel =
        TvMultiEpgViewModel(
            handle,
            receiver.repository,
            receiver.services,
            TimerRepository(
                enigmaClients(receiver.profiles.repository),
                receiver.profiles.repository,
                receiver.profiles.database
            ),
            receiver.profiles.repository,
            receiver.sessions
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
