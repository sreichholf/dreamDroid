package net.reichholf.dreamdroid.ui.setup

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ReceiverDiscovery
import net.reichholf.dreamdroid.data.ReceiverProfileCheckRepository
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.SavedTextField
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [SetupAssistantViewModel] over the real profile and check repositories, with a
 * [MockWebServer] receiver. Discovery is a fake: nothing on the JVM answers mDNS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupAssistantViewModelTest {
    private val server = MockWebServer()
    private val testProfiles = TestProfiles()
    private val profiles = testProfiles.repository
    private val viewModels = mutableListOf<ViewModel>()
    private var searches = 0

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun searchRunsOnceAndListsReceivers() = runTest {
        val viewModel = viewModel(SavedStateHandle())

        viewModel.search()
        viewModel.search()
        val state = viewModel.uiState.first { it.searched }

        assertEquals(listOf(SetupReceiver("dm920", "10.0.0.9", 80)), state.devices)
        assertFalse(state.searching)
        assertEquals(1, searches)
    }

    @Test
    fun checkReachesTheReceiverAndSaveActivatesTheProfile() = runTest {
        server.enqueue(MockResponse().setBody(loadWebFixture("deviceinfo.xml")))
        val viewModel = viewModel(SavedStateHandle())
        viewModel.advance()
        type(viewModel.host, server.hostName)
        viewModel.advance()
        type(viewModel.port, server.port.toString())
        viewModel.advance()

        viewModel.advance()
        val checked = viewModel.uiState.first { it.checkResult != null }

        assertFalse(checked.checkResult!!.hasError)
        assertEquals("/web/deviceinfo", server.takeRequest().requestUrl?.encodedPath)
        viewModel.advance()
        assertEquals(SetupStep.Name, viewModel.uiState.value.draft.step)
        assertEquals(server.hostName, viewModel.profileName.text)

        viewModel.advance()
        val finished = viewModel.uiState.first { it.finished }

        val saved = profiles.profiles().single()
        assertEquals(server.hostName, saved.host)
        assertEquals(server.port, saved.port)
        assertEquals(saved.id, profiles.requireCurrent().id)
        assertEquals(SetupDraft(), finished.draft)
        assertEquals("", viewModel.host.text)
        assertEquals("80", viewModel.port.text)
        viewModel.onFinishHandled()
        assertFalse(viewModel.uiState.value.finished)
    }

    @Test
    fun notAReceiverIsAnError() = runTest {
        // An HTTP error resolves its text through Resources, which the JVM only stubs.
        server.enqueue(MockResponse().setBody("<html>not a receiver</html>"))
        val viewModel = viewModel(SavedStateHandle())
        type(viewModel.host, server.hostName)
        type(viewModel.port, server.port.toString())

        viewModel.check()
        val result = viewModel.uiState.first { it.checkResult != null }.checkResult!!

        assertTrue(result.hasError)
        assertTrue(profiles.profiles().isEmpty())
    }

    @Test
    fun editingTheDraftDropsTheCheckResult() = runTest {
        server.enqueue(MockResponse().setBody(loadWebFixture("deviceinfo.xml")))
        val viewModel = viewModel(SavedStateHandle())
        type(viewModel.host, server.hostName)
        type(viewModel.port, server.port.toString())
        viewModel.check()
        viewModel.uiState.first { it.checkResult != null }

        type(viewModel.user, "admin")

        assertEquals(null, viewModel.uiState.value.checkResult)
        assertFalse(viewModel.uiState.value.checking)
    }

    @Test
    fun draftAndTypedTextSurviveInSavedState() {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.advance()
        type(first.host, "10.0.0.7")
        first.onHttpsChange(true)
        type(first.user, "admin")

        val restored = viewModel(handle)

        val draft = restored.uiState.value.draft
        assertEquals(SetupStep.Find, draft.step)
        assertTrue(draft.useHttps)
        assertEquals("10.0.0.7", restored.host.text)
        assertEquals("443", restored.port.text)
        assertEquals("admin", restored.user.text)
        assertEquals("dreambox", restored.pass.text)
    }

    @Test
    fun pickingAReceiverIsNotATypedEdit() {
        val viewModel = viewModel(SavedStateHandle())
        viewModel.advance()

        viewModel.onPick(SetupReceiver("dm920", "10.0.0.9", 443))
        Snapshot.sendApplyNotifications()

        assertEquals("dm920", viewModel.uiState.value.draft.suggestedName)
        assertEquals("443", viewModel.port.text)
        type(viewModel.host, "10.0.0.10")
        assertEquals("", viewModel.uiState.value.draft.suggestedName)
    }

    @Test
    fun typingANameKeepsItOverTheSuggestion() {
        val viewModel = viewModel(SavedStateHandle())
        type(viewModel.profileName, "Living room")

        assertTrue(viewModel.uiState.value.draft.nameEdited)
    }

    /** Types into [field] the way the text field does, then lets observers see it. */
    private fun type(field: SavedTextField, text: String) {
        field.state.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    private fun viewModel(handle: SavedStateHandle) = SetupAssistantViewModel(
        handle,
        profiles,
        ReceiverProfileCheckRepository(
            profiles,
            enigmaClients(profiles, testProfiles.context)
        ),
        ReceiverDiscovery {
            searches++
            listOf(
                Profile.getDefault().apply {
                    name = "dm920"
                    host = "10.0.0.9"
                    port = 80
                }
            )
        }
    ).also { viewModels += it }
}
