package net.reichholf.dreamdroid.ui.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.testutil.TestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ShareViewModel] over the real repositories and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {
    private val receiver = TestReceiver()
    private val profiles = receiver.repository
    private val viewModels = mutableListOf<ShareViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
        receiver.respond(PLAY, simpleResult(true, "ok"))
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun severalProfilesAreListedAndTheClickedOneGetsTheStream() = runTest {
        saveProfile("Living Room")
        saveProfile("Bedroom")
        val viewModel = viewModel()
        viewModel.start(ShareRequest(url = "http://example.com/a b.ts", title = "Stream"))

        val listed = viewModel.uiState.first { it.profiles.isNotEmpty() }
        assertEquals(listOf("Living Room", "Bedroom"), listed.profiles.map { it.name })
        assertTrue(receiver.requestsTo(PLAY).isEmpty())

        viewModel.onProfileClick(listed.profiles.last())
        val done = viewModel.uiState.first { it.finished }

        assertFalse(done.sending)
        assertEquals(UiText.Resource(R.string.sent_as, listOf("Stream")), done.userMessage)
        assertEquals(
            "4097:0:1:0:0:0:0:0:0:0:http%3A%2F%2Fexample.com%2Fa%20b.ts:Stream",
            receiver.requestsTo(PLAY).single().requestUrl?.queryParameter("file")
        )
    }

    @Test
    fun aSingleProfilePlaysWithoutAsking() = runTest {
        saveProfile("Only")
        val viewModel = viewModel()
        viewModel.start(ShareRequest(url = "https://youtu.be/abc123", title = "Clip"))

        val done = viewModel.uiState.first { it.finished }

        assertTrue(done.profiles.isEmpty())
        assertEquals(
            "8193:0:1:0:0:0:0:0:0:0:yt%3A%2F%2Fabc123:Clip",
            receiver.requestsTo(PLAY).single().requestUrl?.queryParameter("file")
        )
    }

    @Test
    fun rejectedPlayShowsTheReceiverText() = runTest {
        receiver.respond(PLAY, simpleResult(false, "Cannot play"))
        saveProfile("Only")
        val viewModel = viewModel()
        viewModel.start(ShareRequest(url = "http://example.com/a.ts", title = "Stream"))

        val done = viewModel.uiState.first { it.finished }

        assertEquals(UiText.Raw("Cannot play"), done.userMessage)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun aFailureWithoutTextShowsTheGenericError() = runTest {
        receiver.respond(PLAY, MockResponse().setStatus("HTTP/1.1 500 "))
        saveProfile("Only")
        val viewModel = viewModel()
        viewModel.start(ShareRequest(url = "http://example.com/a.ts", title = "Stream"))

        val done = viewModel.uiState.first { it.finished }

        assertEquals(UiText.Resource(R.string.get_content_error), done.userMessage)
    }

    @Test
    fun aYoutubeLinkWithUnescapedCharactersStillPlaysAsYoutube() = runTest {
        saveProfile("Only")
        val viewModel = viewModel()
        viewModel.start(ShareRequest(url = "https://youtu.be/abc123?si=a|b", title = "Clip"))

        viewModel.uiState.first { it.finished }

        assertEquals(
            "8193:0:1:0:0:0:0:0:0:0:yt%3A%2F%2Fabc123:Clip",
            receiver.requestsTo(PLAY).single().requestUrl?.queryParameter("file")
        )
    }

    @Test
    fun noProfileSaysSoAndSendsNothing() = runTest {
        val viewModel = viewModel()
        viewModel.start(ShareRequest(url = "http://example.com/a.ts", title = "Stream"))

        val state = viewModel.uiState.first { it.userMessage != null }

        assertEquals(UiText.Resource(R.string.no_profile_available), state.userMessage)
        assertFalse(state.finished)
        assertTrue(receiver.requests.isEmpty())
    }

    @Test
    fun missingUrlFinishesWithoutSending() = runTest {
        saveProfile("Only")
        val viewModel = viewModel()
        viewModel.start(ShareRequest(url = null, title = "Stream"))

        val done = viewModel.uiState.first { it.finished }

        assertNull(done.userMessage)
        assertTrue(receiver.requests.isEmpty())
    }

    @Test
    fun secondStartIsIgnored() = runTest {
        saveProfile("Only")
        val viewModel = viewModel()
        viewModel.start(ShareRequest(url = "http://example.com/a.ts", title = "Stream"))
        viewModel.uiState.first { it.finished }

        viewModel.start(ShareRequest(url = "http://example.com/b.ts", title = "Other"))

        assertEquals(1, receiver.requestsTo(PLAY).size)
    }

    private fun saveProfile(name: String) = runBlocking<Unit> {
        val current = profiles.requireCurrent()
        profiles.save(
            Profile().apply {
                this.name = name
                host = current.host
                port = current.port
            }
        )
    }

    private fun viewModel(): ShareViewModel =
        ShareViewModel(profiles, ReceiverRepository(enigmaClients(profiles), profiles))
            .also { viewModels += it }

    private companion object {
        const val PLAY = "/web/mediaplayerplay"
    }
}
