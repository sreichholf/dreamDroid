package net.reichholf.dreamdroid.ui.signal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [SignalViewModel] over the real [ReceiverRepository] and a [MockWebServer] receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignalViewModelTest {
    private val server = MockWebServer()
    private val sessions = SessionConnectionHolder()
    private val viewModels = mutableListOf<SignalViewModel>()
    private val profiles: ProfileRepository = TestProfiles().repository

    /** Answer for every `/web/signal` request: the meter polls back to back. */
    @Volatile
    private var answer: () -> MockResponse = {
        MockResponse().setBody(loadWebFixture("signal.xml"))
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = answer()
        }
        server.start()
        // EnigmaHttp still reads ProfileRepository.get() for the XML dump flag.
        ProfileRepository.install(profiles)
        profiles.setCurrent(
            Profile().apply {
                id = 1
                name = "test"
                host = server.hostName
                port = server.port
            }
        )
        sessions.onSuccess()
    }

    @AfterEach
    fun tearDown() {
        // A cancelled poll still resumes on Main once its request returns; finish that before
        // Main is reset.
        runBlocking {
            viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
        }
        server.shutdown()
        profiles.clearCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun pollsWhileShownAndKeepsReadingWhenHidden() = runTest {
        val viewModel = viewModel()

        viewModel.onShown()
        val state = viewModel.uiState.first { it.signal != null }

        assertEquals(63, state.signal?.snrPercent)
        assertEquals("12.50 dB", state.signal?.snrDbRaw)
        assertEquals(12.5, state.snrDb)
        assertTrue(state.polling)
        assertEquals(
            UiText.Resource(
                R.string.title_with_status,
                listOf(UiText.Resource(R.string.signal_meter), UiText.Resource(R.string.loading))
            ),
            state.title
        )
        assertEquals("/web/signal", server.takeRequest().requestUrl?.encodedPath)

        viewModel.onHidden()

        val hidden = viewModel.uiState.value
        assertFalse(hidden.polling)
        assertEquals(63, hidden.signal?.snrPercent)
        assertEquals(UiText.Resource(R.string.signal_meter), hidden.title)
    }

    @Test
    fun doesNotPollUntilShown() {
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.polling)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun blockedSessionDoesNotPollAndIgnoresEnableSwitch() {
        val blocked = SessionConnectionHolder()
        val viewModel = viewModel(sessions = blocked)

        viewModel.onShown()
        viewModel.onEnabledChange(false)

        val state = viewModel.uiState.value
        assertTrue(state.blocked)
        assertTrue(state.enabled)
        assertFalse(state.polling)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun goingOfflineStopsPollingAndClearsMeter() = runTest {
        val viewModel = viewModel()
        viewModel.onShown()
        viewModel.uiState.first { it.signal != null }

        sessions.onFailure(
            EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Timeout),
            hasCache = true
        )

        val state = viewModel.uiState.value
        assertTrue(state.blocked)
        assertFalse(state.polling)
        assertNull(state.signal)
        assertTrue(state.enabled)
    }

    @Test
    fun comingOnlineWhileShownStartsPolling() = runTest {
        val status = SessionConnectionHolder()
        val viewModel = viewModel(sessions = status)
        viewModel.onShown()
        assertEquals(0, server.requestCount)

        status.onSuccess()

        val state = viewModel.uiState.first { it.signal != null }
        assertFalse(state.blocked)
        assertTrue(state.polling)
    }

    @Test
    fun switchingOffClearsMeterAndIsRestored() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.onShown()
        viewModel.uiState.first { it.signal != null }

        viewModel.onEnabledChange(false)
        viewModel.onAcousticChange(true)

        val state = viewModel.uiState.value
        assertFalse(state.enabled)
        assertFalse(state.polling)
        assertNull(state.signal)
        val restored = viewModel(handle).uiState.value
        assertFalse(restored.enabled)
        assertTrue(restored.acousticFeedback)
    }

    @Test
    fun httpErrorSwitchesMeterOffWithMessage() = runTest {
        answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()

        viewModel.onShown()
        val state = viewModel.uiState.first { !it.enabled }

        assertFalse(state.polling)
        assertNull(state.signal)
        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.userMessage
        )
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun unparsableReadingSwitchesMeterOff() = runTest {
        answer = { MockResponse().setBody("<html>not a receiver</html>") }
        val viewModel = viewModel()

        viewModel.onShown()
        val state = viewModel.uiState.first { !it.enabled }

        assertEquals(UiText.Resource(R.string.error_parsing), state.userMessage)
        assertEquals(UiText.Resource(R.string.signal_meter), state.title)
    }

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        sessions: SessionConnectionHolder = this.sessions
    ) = SignalViewModel(
        handle,
        ReceiverRepository(EnigmaClientFactory(profiles), profiles),
        sessions
    )
        .also { viewModels += it }
}
