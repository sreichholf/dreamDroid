package net.reichholf.dreamdroid.ui.current

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [HubNowPlayingViewModel] over the real [ReceiverRepository] and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class HubNowPlayingViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val preferences = MemorySharedPreferences()
    private val viewModels = mutableListOf<HubNowPlayingViewModel>()
    private val polls = mutableListOf<Job>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = { MockResponse().setBody(loadWebFixture("getcurrent.xml")) }
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            polls.forEach { it.cancelAndJoin() }
            viewModels.forEach { it.cancelAndJoin() }
        }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun pollPaintsTheCurrentServiceAndEvent() = runBlocking<Unit> {
        val viewModel = viewModel()
        assertEquals(UiText.Resource(R.string.loading), viewModel.uiState.value.headline)

        poll(viewModel)
        val state = viewModel.ready()

        assertEquals(UiText.Raw("Das Erste HD · Tagesschau"), state.headline)
        assertEquals(UiText.Resource(R.string.current_service), state.label)
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", state.shown?.service?.reference)
        assertEquals(1, getCurrentRequests())
    }

    @Test
    fun pollingAgainPaintsTheLastGoodServiceWithoutAnotherRequest() = runBlocking<Unit> {
        val viewModel = viewModel()
        val first = poll(viewModel)
        viewModel.ready()
        first.cancelAndJoin()

        poll(viewModel)

        assertEquals(UiText.Raw("Das Erste HD · Tagesschau"), viewModel.uiState.value.headline)
        Thread.sleep(200)
        assertEquals(1, getCurrentRequests())
    }

    @Test
    fun offlineSessionShowsTheConnectionInsteadOfTheLastGoodService() = runBlocking<Unit> {
        val viewModel = viewModel()
        poll(viewModel)
        viewModel.ready()

        receiver.goOffline()
        val state = viewModel.uiState.first { it.sessionOffline }

        assertNull(state.shown)
        assertEquals(UiText.Resource(R.string.connection), state.label)
        assertEquals(UiText.Resource(R.string.session_offline), state.headline)
        assertTrue(state.streamBlocked)
        viewModel.reload()
        assertEquals(1, getCurrentRequests())
    }

    @Test
    fun emptyAnswerKeepsTheLastGoodService() = runBlocking<Unit> {
        val viewModel = viewModel()
        poll(viewModel)
        viewModel.ready()
        receiver.answer = {
            MockResponse().setBody("<e2currentserviceinformation></e2currentserviceinformation>")
        }

        viewModel.reload()
        awaitRequests(2)
        Thread.sleep(100)

        assertEquals("Das Erste HD", viewModel.uiState.value.shown?.service?.name)
    }

    @Test
    fun lastGoodServiceOfAnotherProfileIsNotShown() = runBlocking<Unit> {
        val viewModel = viewModel()
        poll(viewModel)
        viewModel.ready()
        receiver.answer = { MockResponse().setResponseCode(500) }

        profiles.setCurrent(
            Profile().apply {
                id = 8
                name = "other"
                host = receiver.server.hostName
                port = receiver.server.port
            }
        )
        awaitRequests(2)
        val state = withTimeout(5_000L) { viewModel.uiState.first { it.ready } }

        assertNull(state.shown)
        assertEquals(UiText.Resource(R.string.not_available), state.headline)
    }

    @Test
    fun zapCounterReloadsOncePerNewValue() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.onReloadEpoch(1)
        awaitRequests(1)
        viewModel.onReloadEpoch(1)
        viewModel.onReloadEpoch(0)
        Thread.sleep(100)

        assertEquals(1, getCurrentRequests())
        viewModel.onReloadEpoch(2)
        awaitRequests(2)
    }

    @Test
    fun stripFollowsTheSetting() = runBlocking<Unit> {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.enabled)

        preferences.edit().putBoolean(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, false).apply()

        withTimeout(5_000L) { viewModel.uiState.first { !it.enabled } }
    }

    @Test
    fun sheetStateIsSavedAndClosingReloads() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)

        viewModel.openSheet()
        assertTrue(viewModel(handle).uiState.value.sheetOpen)

        viewModel.closeSheet()
        assertFalse(viewModel.uiState.value.sheetOpen)
        awaitRequests(1)
    }

    @Test
    fun missingStreamPlayerIsReportedUntilShown() {
        val viewModel = viewModel()

        viewModel.onStreamFailed()
        assertEquals(
            UiText.Resource(R.string.missing_stream_player),
            viewModel.uiState.value.userMessage
        )
        viewModel.onMessageShown()

        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun headlineJoinsServiceAndTitleOnceReady() {
        val service = Service("1:0:1:1", "Das Erste HD")
        val both = HubNowPlayingUiState(
            ready = true,
            current = CurrentService(service, now = Event(title = "Tagesschau"))
        )

        assertEquals(UiText.Raw("Das Erste HD · Tagesschau"), both.headline)
        assertEquals(UiText.Resource(R.string.loading), both.copy(ready = false).headline)
        assertEquals(
            UiText.Raw("Das Erste HD"),
            both.copy(current = CurrentService(service)).headline
        )
        assertEquals(
            UiText.Raw("Tagesschau"),
            both.copy(current = CurrentService(now = Event(title = "Tagesschau"))).headline
        )
        assertEquals(
            UiText.Resource(R.string.not_available),
            both.copy(current = null).headline
        )
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = HubNowPlayingViewModel(
        handle,
        ReceiverRepository(enigmaClients(profiles), profiles),
        profiles,
        receiver.sessions,
        SettingsRepository(preferences)
    ).also { viewModels += it }

    private fun poll(viewModel: HubNowPlayingViewModel): Job =
        CoroutineScope(Dispatchers.Main).launch { viewModel.poll() }.also { polls += it }

    private suspend fun HubNowPlayingViewModel.ready(): HubNowPlayingUiState =
        withTimeout(5_000L) { uiState.first { it.ready && it.current != null } }

    private fun getCurrentRequests(): Int = receiver.requestsTo(GET_CURRENT).size

    private fun awaitRequests(count: Int) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (getCurrentRequests() < count) {
            check(System.currentTimeMillis() < deadline) { "no request $count" }
            Thread.sleep(20)
        }
    }

    private companion object {
        const val GET_CURRENT = "/web/getcurrent"
    }
}
