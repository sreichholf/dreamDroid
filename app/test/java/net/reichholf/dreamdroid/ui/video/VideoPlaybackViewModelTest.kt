package net.reichholf.dreamdroid.ui.video

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.TV_ROOTS
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.receiverApis
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
        val state = viewModel.uiState.first { it.stream != null }

        assertEquals(zdfStream(), state.stream)
        assertTrue(receiver.requestsTo(ZAP).isEmpty())
        viewModel.onStreamStarted()
        assertNull(viewModel.uiState.value.stream)
    }

    @Test
    fun zapAndStreamZapsBeforeStreaming() = runTest {
        receiver.profiles.repository.requireCurrent().zapAndStream = true
        val viewModel = viewModel()
        viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)

        viewModel.streamCurrent()
        val state = viewModel.uiState.first { it.stream != null }

        assertEquals(zdfStream(), state.stream)
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
        assertNull(state.stream)
    }

    @Test
    fun extrasBeforeTheProfileLoadWaitForIt() = runTest {
        // A restore after process death: the player comes back before the profile is read.
        val cold = EpgTestReceiver().apply { answer = ::routes }
        try {
            val viewModel = viewModel(on = cold)
            viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)
            assertFalse(cold.profiles.repository.isLoaded())

            cold.start()
            val state = viewModel.uiState.first { it.bouquets.isNotEmpty() }

            assertEquals("Favourites (TV)", state.bouquets.first().name)
        } finally {
            cold.stop()
        }
    }

    @Test
    fun liveExtrasLoadTheBouquetBar() = runTest {
        val viewModel = viewModel()
        viewModel.applyExtras("ZDF HD", ZDF, BOUQUET, null)

        val state = viewModel.uiState.first { it.bouquets.isNotEmpty() }

        assertEquals("Favourites (TV)", state.bouquets.first().name)
        assertEquals(TV_ROOTS[0], receiver.requestsTo(GET_SERVICES).first().sRef())
    }

    @Test
    fun sleepTimerCountsDownTheMinutesThenExpires() = runTest {
        val viewModel = viewModel()

        viewModel.setSleepTimer(2)
        assertEquals(SleepTimer.Running(2), viewModel.uiState.value.sleepTimer)
        assertEquals(
            UiText.Resource(R.string.sleep_timer_set, listOf(2)),
            viewModel.uiState.value.userMessage
        )

        advanceTimeBy(MINUTE_MS + 1)
        assertEquals(SleepTimer.Running(1), viewModel.uiState.value.sleepTimer)

        advanceTimeBy(MINUTE_MS - 10 * SECOND_MS)
        assertEquals(SleepTimer.Closing(10), viewModel.uiState.value.sleepTimer)

        advanceTimeBy(SECOND_MS)
        assertEquals(SleepTimer.Closing(9), viewModel.uiState.value.sleepTimer)

        advanceTimeBy(9 * SECOND_MS)
        assertEquals(SleepTimer.Expired, viewModel.uiState.value.sleepTimer)
    }

    @Test
    fun newSleepTimerRestartsTheCountdown() = runTest {
        val viewModel = viewModel()
        viewModel.setSleepTimer(1)
        advanceTimeBy(MINUTE_MS / 2)

        viewModel.setSleepTimer(15)
        advanceTimeBy(MINUTE_MS / 2 + 1)

        assertEquals(SleepTimer.Running(15), viewModel.uiState.value.sleepTimer)
    }

    @Test
    fun extendingDuringTheClosingCountdownRestartsTheTimer() = runTest {
        val viewModel = viewModel()
        viewModel.setSleepTimer(1)
        advanceTimeBy(MINUTE_MS - 5 * SECOND_MS + 1)
        assertEquals(SleepTimer.Closing(5), viewModel.uiState.value.sleepTimer)

        viewModel.setSleepTimer(SLEEP_TIMER_EXTEND_MINUTES)
        advanceTimeBy(10 * SECOND_MS)

        assertEquals(SleepTimer.Running(15), viewModel.uiState.value.sleepTimer)
    }

    @Test
    fun sleepTimerOffCancelsTheCountdown() = runTest {
        val viewModel = viewModel()
        viewModel.setSleepTimer(1)

        viewModel.setSleepTimer(0)
        advanceUntilIdle()

        assertEquals(SleepTimer.Off, viewModel.uiState.value.sleepTimer)
        assertEquals(
            UiText.Resource(R.string.sleep_timer_cancelled),
            viewModel.uiState.value.userMessage
        )
    }

    private fun zdfStream(): LiveStream.Ready =
        LiveStream.Ready(ZDF, EnigmaUrls.stream(receiver.profiles.repository.requireCurrent(), ZDF))

    private fun viewModel(on: EpgTestReceiver = receiver): VideoPlaybackViewModel =
        VideoPlaybackViewModel(
            on.services,
            ReceiverRepository(
                receiverApis(on.profiles.repository),
                on.profiles.repository
            ),
            on.profiles.repository,
            on.sessions
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
        const val SECOND_MS = 1_000L
        const val MINUTE_MS = 60 * SECOND_MS
        const val BOUQUET = EpgTestReceiver.BOUQUET
        const val ZDF = "1:0:1:6DCB:44D:1:C00000:0:0:0:"
        const val EPG_NOW_NEXT = "/web/epgnownext"
        const val GET_SERVICES = "/web/getservices"
        const val ZAP = "/web/zap"
    }
}
