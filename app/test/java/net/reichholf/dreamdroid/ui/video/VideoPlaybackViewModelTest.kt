package net.reichholf.dreamdroid.ui.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [VideoPlaybackViewModel] over the real repositories and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlaybackViewModelTest {
    private val receiver = EpgTestReceiver()
    private val viewModels = mutableListOf<VideoPlaybackViewModel>()

    /** `/web/zap` answer. */
    private var zapReply = MockResponse().setBody(simpleResult(true, "Active service changed"))

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
    fun reloadLoadsTheBouquetNowNextAndMarksThePlayingService() = runTest {
        val viewModel = viewModel()
        viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)

        viewModel.reload()
        val state = viewModel.uiState.first { it.services.isNotEmpty() }

        assertEquals(
            listOf("Das Erste HD", "ZDF HD", "Arte HD"),
            state.services.map { it.serviceName }.distinct()
        )
        assertEquals(ZDF, state.currentService?.serviceReference)
        val request = receiver.requestsTo(EPG_NOW_NEXT).single()
        assertEquals(BOUQUET, request.requestUrl?.queryParameter("bRef"))
        // The bouquet bar loads alongside the reload; await it before asserting
        // its request, instead of assuming it already fired.
        viewModel.uiState.first { it.bouquets.isNotEmpty() }
        assertTrue(receiver.requestsTo(GET_SERVICES).isNotEmpty())
    }

    @Test
    fun failedReloadShowsTheContentErrorUntilShown() = runTest {
        receiver.answer = { request ->
            if (request.path() == EPG_NOW_NEXT) {
                MockResponse().setResponseCode(500)
            } else {
                routes(request)
            }
        }
        val viewModel = viewModel()
        viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)

        viewModel.reload()
        val state = viewModel.uiState.first { it.userMessage != null }

        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error"))
                .contentErrorText(),
            state.userMessage
        )
        assertTrue(state.services.isEmpty())
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun streamsRightAwayWithoutZapAndStream() = runTest {
        val viewModel = viewModel()
        viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)

        viewModel.streamCurrent()

        assertEquals(ZDF, viewModel.uiState.value.streamRef)
        assertTrue(receiver.requestsTo(ZAP).isEmpty())
        viewModel.onStreamStarted()
        assertNull(viewModel.uiState.value.streamRef)
    }

    @Test
    fun zapAndStreamZapsBeforeStreaming() = runTest {
        receiver.profiles.repository.requireCurrent().zapAndStream = true
        val viewModel = viewModel()
        viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)

        viewModel.streamCurrent()
        val state = viewModel.uiState.first { it.streamRef != null }

        assertEquals(ZDF, state.streamRef)
        assertEquals(ZDF, receiver.requestsTo(ZAP).single().requestUrl?.queryParameter("sRef"))
    }

    @Test
    fun rejectedZapShowsTheReceiverTextAndStreamsNothing() = runTest {
        receiver.profiles.repository.requireCurrent().zapAndStream = true
        zapReply = MockResponse().setBody(simpleResult(false, "No free tuner"))
        val viewModel = viewModel()
        viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)

        viewModel.streamCurrent()
        val state = viewModel.uiState.first { it.userMessage != null }

        assertEquals(UiText.Raw("No free tuner"), state.userMessage)
        assertNull(state.streamRef)
    }

    @Test
    fun liveExtrasLoadTheBouquetBar() = runTest {
        val viewModel = viewModel()
        viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)

        val state = viewModel.uiState.first { it.bouquets.isNotEmpty() }

        assertEquals("Favourites (TV)", state.bouquets.first().name)
        assertEquals(TV_ROOTS[0], receiver.requestsTo(GET_SERVICES).first().sRef())
    }

    private fun viewModel(): VideoPlaybackViewModel = VideoPlaybackViewModel(
        receiver.services,
        ReceiverRepository(
            enigmaClients(receiver.profiles.repository),
            receiver.profiles.repository
        ),
        receiver.profiles.repository,
        receiver.sessions
    ).also { viewModels += it }

    private fun routes(request: RecordedRequest): MockResponse = when (request.path()) {
        EPG_NOW_NEXT -> MockResponse().setBody(loadWebFixture("epgnownext.xml"))
        GET_SERVICES -> MockResponse().setBody(loadWebFixture("getservices.xml"))
        ZAP -> zapReply
        else -> MockResponse().setResponseCode(404)
    }

    private fun RecordedRequest.path(): String? = requestUrl?.encodedPath

    private fun RecordedRequest.sRef(): String? = requestUrl?.queryParameter("sRef")

    private companion object {
        const val BOUQUET = EpgTestReceiver.BOUQUET
        const val ZDF = "1:0:1:6DCB:44D:1:C00000:0:0:0:"
        const val EPG_NOW_NEXT = "/web/epgnownext"
        const val GET_SERVICES = "/web/getservices"
        const val ZAP = "/web/zap"
    }
}
