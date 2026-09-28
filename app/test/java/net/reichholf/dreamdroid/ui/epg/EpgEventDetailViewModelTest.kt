package net.reichholf.dreamdroid.ui.epg

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [EpgEventDetailViewModel] against a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class EpgEventDetailViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<EpgEventDetailViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun shownEventIsRestoredUntilDismissed() {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)

        viewModel.showDetail(EVENT.copy(startReadable = "20:00"))

        assertEquals("Tagesschau", viewModel.uiState.value.event?.title)
        assertEquals("Tagesschau", viewModel(handle).uiState.value.event?.title)
        viewModel.dismissDetail()
        assertNull(viewModel.uiState.value.event)
        assertNull(viewModel(handle).uiState.value.event)
    }

    @Test
    fun roomEventGetsReadableTimes() {
        val viewModel = viewModel()

        viewModel.showDetail(EVENT)

        assertTrue(viewModel.uiState.value.event!!.startReadable.isNotEmpty())
    }

    @Test
    fun setTimerAddsByEventIdAndReportsTheReceiverText() = runTest {
        receiver.answer = { MockResponse().setBody(result("True", "Timer added")) }
        val viewModel = viewModel()

        viewModel.setTimer(EVENT)
        val state = viewModel.uiState.first { it.userMessage != null }

        assertFalse(state.saving)
        assertEquals(UiText.Raw("Timer added"), state.userMessage)
        val url = receiver.server.takeRequest().requestUrl!!
        assertEquals("/web/timeraddbyeventid", url.encodedPath)
        assertEquals(EVENT.serviceReference, url.queryParameter("sRef"))
        assertEquals(EVENT.eventId, url.queryParameter("eventid"))
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun rejectedTimerReportsTheReceiverReason() = runTest {
        receiver.answer = { MockResponse().setBody(result("False", "Conflict")) }
        val viewModel = viewModel()

        viewModel.setTimer(EVENT)

        assertEquals(UiText.Raw("Conflict"), viewModel.uiState.first { !it.saving }.userMessage)
    }

    @Test
    fun httpFailureReportsTheFailure() = runTest {
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()

        viewModel.setTimer(EVENT)

        assertEquals(
            UiText.Raw("Server Error"),
            viewModel.uiState.first { !it.saving }.userMessage
        )
    }

    @Test
    fun secondSetTimerWhileSavingIsIgnored() = runTest {
        val release = CountDownLatch(1)
        receiver.answer = {
            release.await(10, TimeUnit.SECONDS)
            MockResponse().setBody(result("True", "Timer added"))
        }
        val viewModel = viewModel()

        viewModel.setTimer(EVENT)
        assertTrue(viewModel.uiState.value.saving)
        viewModel.setTimer(EVENT)
        release.countDown()
        viewModel.uiState.first { !it.saving }

        assertEquals(1, receiver.server.requestCount)
    }

    private fun result(state: String, text: String) =
        "<e2simplexmlresult><e2state>$state</e2state><e2statetext>$text</e2statetext>" +
            "</e2simplexmlresult>"

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        EpgEventDetailViewModel(handle, EnigmaClientFactory(receiver.profiles.repository))
            .also { viewModels += it }

    private companion object {
        val EVENT = Event(
            eventId = "39150",
            title = "Tagesschau",
            start = "1893456000",
            duration = "900",
            serviceReference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
            serviceName = "Das Erste HD"
        )
    }
}
