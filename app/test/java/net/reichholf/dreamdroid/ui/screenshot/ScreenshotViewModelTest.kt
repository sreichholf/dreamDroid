package net.reichholf.dreamdroid.ui.screenshot

import androidx.lifecycle.viewModelScope
import java.util.concurrent.TimeUnit
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
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ScreenshotViewModel] over the real [ReceiverRepository] and a [MockWebServer] receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotViewModelTest {
    private val server = MockWebServer()
    private val sessions = SessionConnectionHolder()
    private val viewModels = mutableListOf<ScreenshotViewModel>()
    private val profiles: ProfileRepository = TestProfiles().repository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
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
        // A cancelled grab still resumes on Main once its request returns; finish that before
        // Main is reset.
        runBlocking {
            viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
        }
        server.shutdown()
        profiles.clearCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun grabsJpegWhenCreated() = runTest {
        server.enqueue(image(JPEG))

        val state = viewModel().settled()

        assertArrayEquals(JPEG, state.image)
        assertNull(state.userMessage)
        assertEquals(UiText.Resource(R.string.screenshot), state.title)
        val request = server.takeRequest().requestUrl
        assertEquals("/grab", request?.encodedPath)
        assertEquals("jpg", request?.queryParameter("format"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun pngIsAnImageToo() = runTest {
        server.enqueue(image(PNG))

        assertArrayEquals(PNG, viewModel().settled().image)
    }

    @Test
    fun emptyGrabFallsBackToWebScreenshot() = runTest {
        server.enqueue(MockResponse())
        server.enqueue(image(JPEG))

        val state = viewModel().settled()

        assertArrayEquals(JPEG, state.image)
        assertNull(state.userMessage)
        assertEquals("/grab", nextRequest()?.encodedPath)
        val fallback = nextRequest()
        assertEquals("/screenshot", fallback?.encodedPath)
        assertEquals("jpg", fallback?.queryParameter("format"))
        assertEquals("1", fallback?.queryParameter("osd"))
        assertEquals("1", fallback?.queryParameter("video"))
    }

    @Test
    fun nonImageBodiesShowError() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>401 Unauthorized</body></html>"))
        server.enqueue(MockResponse().setBody("<html><body>401 Unauthorized</body></html>"))

        val state = viewModel().settled()

        assertNull(state.image)
        assertEquals(UiText.Resource(R.string.error), state.userMessage)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun missingWebScreenshotKeepsGrabError() = runTest {
        server.enqueue(MockResponse())
        server.enqueue(MockResponse().setResponseCode(404))

        val state = viewModel().settled()

        assertNull(state.image)
        assertEquals(UiText.Resource(R.string.error), state.userMessage)
        assertEquals("/grab", nextRequest()?.encodedPath)
        assertEquals("/screenshot", nextRequest()?.encodedPath)
    }

    @Test
    fun httpErrorShowsFailureText() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val state = viewModel().settled()

        assertNull(state.image)
        assertEquals(
            EnigmaFailure.fromHttpStatus(500, "Server Error").userMessageText(),
            state.userMessage
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun failedRefreshKeepsShownImage() = runTest {
        server.enqueue(image(JPEG))
        server.enqueue(MockResponse().setResponseCode(500))
        val viewModel = viewModel()
        val first = viewModel.settled().image

        viewModel.refresh()
        val state = viewModel.settled()

        assertSame(first, state.image)
        assertEquals(UiText.Raw("Server Error"), state.userMessage)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun blockedSessionDoesNotGrab() {
        val viewModel = viewModel(SessionConnectionHolder())

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertTrue(state.blocked)
        assertFalse(state.loading)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun blockedFollowsSession() {
        server.enqueue(image(JPEG))
        val viewModel = viewModel()

        sessions.onFailure(EnigmaFailure.Auth, hasCache = true)

        assertTrue(viewModel.uiState.value.blocked)
    }

    @Test
    fun saveOutcomeBecomesUserMessage() {
        val viewModel = viewModel(SessionConnectionHolder())

        viewModel.onSaved("dreamDroid_1.jpg")
        assertEquals(
            UiText.Resource(R.string.screenshot_saved, listOf("dreamDroid_1.jpg")),
            viewModel.uiState.value.userMessage
        )
        viewModel.onFileFailed()
        assertEquals(UiText.Resource(R.string.error), viewModel.uiState.value.userMessage)
    }

    private fun viewModel(sessions: SessionConnectionHolder = this.sessions) =
        ScreenshotViewModel(ReceiverRepository(enigmaClients(profiles), profiles), sessions)
            .also { viewModels += it }

    private suspend fun ScreenshotViewModel.settled(): ScreenshotUiState =
        uiState.first { !it.loading }

    private fun nextRequest() = server.takeRequest(5, TimeUnit.SECONDS)?.requestUrl

    private fun image(bytes: ByteArray) = MockResponse().setBody(Buffer().write(bytes))

    private companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
    }
}
