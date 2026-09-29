package net.reichholf.dreamdroid.ui.zap

import androidx.lifecycle.SavedStateHandle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
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

/** [ZapViewModel] over the real repositories and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class ZapViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val viewModels = mutableListOf<ZapViewModel>()

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
    fun defaultBouquetLoadsWithoutMarkers() = runBlocking<Unit> {
        useDefaultBouquet()

        val state = viewModel().settled()

        assertEquals(
            listOf("Favourites (TV)", "Das Erste HD"),
            state.items.map { it.name }
        )
        assertNull(state.emptyMessage)
        assertEquals(UiText.Raw("Favourites"), state.title)
        assertEquals(BOUQUET, receiver.requestsTo(GET_SERVICES).single().sRef())
    }

    @Test
    fun titleSaysLoadingWhileRefreshing() = runBlocking<Unit> {
        useDefaultBouquet()
        val release = CountDownLatch(1)
        receiver.answer = { request ->
            release.await(5, TimeUnit.SECONDS)
            routes(request)
        }
        val viewModel = viewModel()

        val loading = viewModel.uiState.value
        assertTrue(loading.refreshing)
        assertEquals(UiText.Resource(R.string.loading), loading.title)
        assertEquals(UiText.Resource(R.string.loading), loading.emptyMessage)
        release.countDown()
        assertEquals(UiText.Raw("Favourites"), viewModel.settled().title)
    }

    @Test
    fun withoutABouquetThePickerOpensOnceAndItsAnswerLoads() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)

        assertEquals(ZapEffect.PickBouquet, viewModel.uiState.value.effect)
        assertEquals(UiText.Resource(R.string.app_name), viewModel.uiState.value.title)
        viewModel.onEffectHandled()
        viewModel.reload()
        assertNull(viewModel.uiState.value.effect)

        viewModel.onBouquetPicked(Service(BOUQUET, "Picked"))
        val state = viewModel.settled()

        assertEquals(2, state.items.size)
        assertEquals(1, state.scrollEpoch)
        assertEquals(UiText.Raw("Picked"), state.title)
        assertEquals(ZapNavSaved(BOUQUET, "Picked", false), readZapNavSaved(handle, "", ""))
    }

    @Test
    fun cancelledPickerSaysNoItemsAndTheNextReloadAsksAgain() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.onEffectHandled()

        viewModel.onBouquetPickCancelled()

        assertEquals(
            UiText.Resource(R.string.no_list_item),
            viewModel.uiState.value.emptyMessage
        )
        viewModel.reload()
        assertEquals(ZapEffect.PickBouquet, viewModel.uiState.value.effect)
    }

    @Test
    fun cancelledPickerKeepsALoadedGrid() = runBlocking<Unit> {
        useDefaultBouquet()
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.pickBouquet()
        viewModel.onBouquetPickCancelled()

        assertNull(viewModel.uiState.value.emptyMessage)
        assertEquals(2, viewModel.uiState.value.items.size)
    }

    @Test
    fun pickingTheSameBouquetDoesNotScroll() = runBlocking<Unit> {
        useDefaultBouquet()
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.onBouquetPicked(Service(BOUQUET, "Favourites"))

        assertEquals(0, viewModel.settled().scrollEpoch)
        assertEquals(2, receiver.requestsTo(GET_SERVICES).size)
    }

    @Test
    fun savedBouquetWinsOverTheProfileDefault() = runBlocking<Unit> {
        useDefaultBouquet()
        val handle = SavedStateHandle()
        ZapNavSaved(OTHER, "Other", waitingForPicker = false).writeTo(handle)

        val state = viewModel(handle).settled()

        assertEquals(UiText.Raw("Other"), state.title)
        assertEquals(OTHER, receiver.requestsTo(GET_SERVICES).single().sRef())
    }

    @Test
    fun failureShowsTheError() = runBlocking<Unit> {
        useDefaultBouquet()
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel().settled()

        assertTrue(state.items.isEmpty())
        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.emptyMessage
        )
    }

    @Test
    fun zapSendsTheServiceAndReportsTheReply() = runBlocking<Unit> {
        useDefaultBouquet()
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.zap(Service(CHANNEL, "Das Erste HD"))
        val state = withTimeout(5_000L) { viewModel.uiState.first { it.userMessage != null } }

        assertEquals(UiText.Raw("Zapped"), state.userMessage)
        assertEquals(CHANNEL, receiver.requestsTo(ZAP).single().sRef())
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun blockedSessionDoesNotZap() = runBlocking<Unit> {
        useDefaultBouquet()
        val viewModel = viewModel()
        viewModel.settled()
        receiver.goOffline()

        assertTrue(viewModel.uiState.value.zapBlocked)
        viewModel.zap(Service(CHANNEL, "Das Erste HD"))
        Thread.sleep(100)

        assertTrue(receiver.requestsTo(ZAP).isEmpty())
    }

    @Test
    fun longPressStreamsThroughTheDestination() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.onEffectHandled()
        val service = Service(CHANNEL, "Das Erste HD")

        viewModel.stream(service)
        val effect = withTimeout(5_000L) {
            viewModel.uiState.first { it.effect is ZapEffect.Stream }
        }.effect
        assertEquals(
            ZapEffect.Stream(
                service,
                LiveStream.Ready(CHANNEL, EnigmaUrls.stream(profiles.requireCurrent(), CHANNEL))
            ),
            effect
        )
        viewModel.onEffectHandled()
        viewModel.onStreamFailed()

        assertNull(viewModel.uiState.value.effect)
        assertEquals(
            UiText.Resource(R.string.missing_stream_player),
            viewModel.uiState.value.userMessage
        )
        assertFalse(viewModel.uiState.value.zapBlocked)
    }

    @Test
    fun rejectedZapAndStreamShowsTheReceiverText() = runBlocking<Unit> {
        profiles.requireCurrent().zapAndStream = true
        receiver.answer = { request ->
            if (request.requestUrl?.encodedPath == ZAP) {
                MockResponse().setBody(simpleResult(false, "No free tuner"))
            } else {
                routes(request)
            }
        }
        val viewModel = viewModel()
        viewModel.onEffectHandled()

        viewModel.stream(Service(CHANNEL, "Das Erste HD"))
        val state = withTimeout(5_000L) { viewModel.uiState.first { it.userMessage != null } }

        assertEquals(UiText.Raw("No free tuner"), state.userMessage)
        assertNull(state.effect)
        assertEquals(CHANNEL, receiver.requestsTo(ZAP).single().sRef())
    }

    private fun useDefaultBouquet() {
        profiles.requireCurrent().setDefaultRefValues(BOUQUET, "Favourites")
    }

    private fun routes(request: RecordedRequest): MockResponse =
        when (request.requestUrl?.encodedPath) {
            GET_SERVICES -> MockResponse().setBody(loadWebFixture("getservices.xml"))

            ZAP -> MockResponse().setBody(
                "<e2simplexmlresult><e2state>True</e2state>" +
                    "<e2statetext>Zapped</e2statetext></e2simplexmlresult>"
            )

            else -> MockResponse().setResponseCode(404)
        }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = ZapViewModel(
        handle,
        receiver.services,
        ReceiverRepository(enigmaClients(profiles), profiles),
        receiver.sessions,
        profiles
    ).also { viewModels += it }

    private suspend fun ZapViewModel.settled(): ZapUiState =
        withTimeout(5_000L) { uiState.first { !it.refreshing } }

    private fun RecordedRequest.sRef(): String? = requestUrl?.queryParameter("sRef")

    private companion object {
        const val GET_SERVICES = "/web/getservices"
        const val ZAP = "/web/zap"
        const val BOUQUET = EpgTestReceiver.BOUQUET
        const val OTHER = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.other.tv\""
        const val CHANNEL = EpgTestReceiver.CHANNEL
    }
}
