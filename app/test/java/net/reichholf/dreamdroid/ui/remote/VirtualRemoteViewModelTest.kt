package net.reichholf.dreamdroid.ui.remote

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [VirtualRemoteViewModel] over the real repositories and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class VirtualRemoteViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val preferences = MemorySharedPreferences()
    private val viewModels = mutableListOf<VirtualRemoteViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = { request ->
            if (request.requestUrl?.encodedPath == "/web/remotecontrol") {
                MockResponse().setBody(simpleResult(true, "Ok"))
            } else {
                MockResponse().setResponseCode(404)
            }
        }
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun defaultPageIsFull() = runBlocking {
        val state = viewModel().uiState.first { it.layout == VirtualRemoteLayout.Full }

        assertEquals(0, state.page)
        assertEquals(UiText.Resource(R.string.virtual_remote), state.title)
        assertNull(state.userMessage)
    }

    @Test
    fun simpleVrmOffOpensQuickZap() = runBlocking {
        preferences.edit().putBoolean(DreamDroid.PREFS_KEY_SIMPLE_VRM, false).commit()

        val state = viewModel().uiState.first { it.layout == VirtualRemoteLayout.QuickZap }

        assertEquals(1, state.page)
        assertEquals(UiText.Resource(R.string.quickzap), state.title)
    }

    @Test
    fun toggleFlipsThePageAndPersistsIt() = runBlocking {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.awaitState { it.layout == VirtualRemoteLayout.Full }

        viewModel.onToggleLayout()
        val toggled = viewModel.awaitState { it.page == 1 }

        assertEquals(VirtualRemoteLayout.QuickZap, toggled.layout)
        assertEquals(1, viewModel(handle).uiState.value.page)
    }

    @Test
    fun profileSimpleRemotePicksSimpleLayout() = runBlocking {
        receiver.profiles.database.profileDao().addProfile(
            Profile().apply {
                id = 8
                name = "simple"
                host = "simple-box"
                port = 80
                simpleRemote = true
            }
        )
        val viewModel = viewModel()
        viewModel.awaitState { it.layout == VirtualRemoteLayout.Full }

        profiles.setCurrent(8)
        val state = viewModel.awaitState { it.layout == VirtualRemoteLayout.Simple }

        assertTrue(state.simpleRemote)
    }

    @Test
    fun acceptedKeyReloadsTheScreenshot() = runBlocking {
        val viewModel = viewModel()
        viewModel.awaitState { it.layout == VirtualRemoteLayout.Full }

        viewModel.onKey(412, false)
        val state = viewModel.awaitState { it.screenshotEpoch == 1 }

        assertNull(state.userMessage)
        val request = receiver.requests.single().requestUrl!!
        assertEquals("412", request.queryParameter("command"))
        assertEquals("advanced", request.queryParameter("rcu"))
        assertNull(request.queryParameter("type"))
        viewModel.onMessageShown()
    }

    @Test
    fun rejectedKeyShowsTheReceiverText() = runBlocking {
        receiver.answer = { MockResponse().setBody(simpleResult(false, "No free tuner")) }
        val viewModel = viewModel()
        viewModel.awaitState { it.layout == VirtualRemoteLayout.Full }

        viewModel.onKey(412, true)
        val state = viewModel.awaitState { it.userMessage != null }

        assertEquals(UiText.Raw("No free tuner"), state.userMessage)
        assertEquals(0, state.screenshotEpoch)
        val request = receiver.requests.single().requestUrl!!
        assertEquals("long", request.queryParameter("type"))
    }

    @Test
    fun blockedSessionSendsNothing() = runBlocking {
        receiver.goOffline()
        val viewModel = viewModel()
        viewModel.awaitState { it.keysBlocked }

        viewModel.onKey(412, false)

        assertTrue(receiver.requests.isEmpty())
        assertNull(viewModel.uiState.value.userMessage)
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): VirtualRemoteViewModel =
        VirtualRemoteViewModel(
            handle,
            ReceiverRepository(enigmaClients(profiles), profiles),
            profiles,
            receiver.sessions,
            SettingsRepository(preferences)
        ).also { viewModels += it }

    private suspend fun VirtualRemoteViewModel.awaitState(
        condition: (VirtualRemoteUiState) -> Boolean
    ): VirtualRemoteUiState = withTimeout(5_000L) { uiState.first(condition) }
}
