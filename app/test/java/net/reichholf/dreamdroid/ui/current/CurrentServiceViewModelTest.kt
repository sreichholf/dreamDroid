package net.reichholf.dreamdroid.ui.current

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
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LastLiveStream
import net.reichholf.dreamdroid.data.LastLiveStreamRepository
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
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

/** [CurrentServiceViewModel] over the real [ReceiverRepository] and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentServiceViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val preferences = MemorySharedPreferences()
    private val lastStreams = LastLiveStreamRepository(preferences, profiles)
    private val viewModels = mutableListOf<CurrentServiceViewModel>()

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
    fun loadsTheCurrentServiceAndSavesIt() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val state = viewModel(handle).settled()

        assertTrue(state.ready)
        assertEquals("Das Erste HD", state.current?.service?.name)
        assertEquals("Tagesschau", state.current?.now?.title)
        assertTrue(state.canStream)
        assertEquals(UiText.Resource(R.string.current_service), state.title)
        assertEquals(
            state.current,
            readCurrentServiceSaved(handle, EpgTestReceiver.PROFILE_ID).current
        )
    }

    @Test
    fun titleSaysLoadingWhileRefreshing() = runBlocking<Unit> {
        val release = CountDownLatch(1)
        receiver.answer = {
            release.await(5, TimeUnit.SECONDS)
            MockResponse().setBody(loadWebFixture("getcurrent.xml"))
        }
        val viewModel = viewModel()

        val loading = viewModel.uiState.value
        assertTrue(loading.refreshing)
        assertFalse(loading.ready)
        assertEquals(UiText.Resource(R.string.loading), loading.title)
        release.countDown()
        assertEquals(UiText.Resource(R.string.current_service), viewModel.settled().title)
    }

    @Test
    fun savedServiceOfThisProfilePaintsWithoutARequest() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        CurrentServiceSaved(SAVED, EpgTestReceiver.PROFILE_ID).writeTo(handle)

        val state = viewModel(handle).uiState.value

        assertEquals(SAVED, state.current)
        assertTrue(state.ready)
        // Each load starts through reload(), which marks the state refreshing first.
        assertFalse(state.refreshing)
        assertTrue(receiver.requests.isEmpty())
    }

    @Test
    fun savedServiceOfAnotherProfileLoadsAgain() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        CurrentServiceSaved(SAVED, profileId = 99).writeTo(handle)

        val state = viewModel(handle).settled()

        assertEquals("Das Erste HD", state.current?.service?.name)
        assertEquals(1, receiver.requests.size)
    }

    @Test
    fun failureWithoutLastGoodIsReadyAndEmpty() = runBlocking<Unit> {
        receiver.answer = { MockResponse().setResponseCode(500) }

        val state = viewModel().settled()

        assertTrue(state.ready)
        assertNull(state.current)
        assertFalse(state.canStream)
    }

    @Test
    fun failedReloadKeepsTheLastGoodService() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.settled()
        receiver.answer = { MockResponse().setResponseCode(500) }

        viewModel.reload()
        val state = viewModel.settled()

        assertEquals("Das Erste HD", state.current?.service?.name)
        assertEquals(2, receiver.requests.size)
    }

    @Test
    fun profileSwitchDropsTheOtherProfilesServiceAndLoadsAgain() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.settled()
        receiver.answer = { MockResponse().setResponseCode(500) }

        profiles.setCurrent(
            Profile().apply {
                id = 8
                name = "other"
                host = receiver.server.hostName
                port = receiver.server.port
            }
        )
        val state = withTimeout(5_000L) {
            viewModel.uiState.first { receiver.requests.size == 2 && it.ready && !it.refreshing }
        }

        assertNull(state.current)
        assertNull(readCurrentServiceSaved(handle, 8).current)
    }

    @Test
    fun streamBlockAndPiconsFollowSessionAndSettings() = runBlocking<Unit> {
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.streamBlocked)
        assertFalse(viewModel.uiState.value.piconsEnabled)

        receiver.goOffline()
        preferences.edit().putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, true).apply()

        withTimeout(5_000L) {
            viewModel.uiState.first { it.streamBlocked && it.piconsEnabled }
        }
    }

    @Test
    fun streamHandsTheServiceOnScreenToThePlayerWithTheProfileBouquet() = runBlocking<Unit> {
        profiles.current.value!!.setDefaultRefValues(BOUQUET, "Favourites")
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.stream()
        val stream = withTimeout(5_000L) { viewModel.uiState.first { it.stream != null } }.stream

        assertEquals(
            CurrentServiceStream(
                "Das Erste HD",
                BOUQUET,
                LiveStream.Ready(REF, EnigmaUrls.stream(profiles.current.value!!, REF))
            ),
            stream
        )
        viewModel.onStreamStarted()
        assertNull(viewModel.uiState.value.stream)
    }

    @Test
    fun streamPrefersTheBouquetTheServiceLastStreamedFrom() = runBlocking<Unit> {
        profiles.current.value!!.setDefaultRefValues(BOUQUET, "Favourites")
        lastStreams.save(LastLiveStream(REF, "Das Erste HD", LAST_STREAM_BOUQUET))
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.stream()
        val stream = withTimeout(5_000L) { viewModel.uiState.first { it.stream != null } }.stream

        assertEquals(LAST_STREAM_BOUQUET, stream?.bouquetRef)
    }

    @Test
    fun streamOfAnotherServiceFallsBackToTheProfileBouquet() = runBlocking<Unit> {
        profiles.current.value!!.setDefaultRefValues(BOUQUET, "Favourites")
        lastStreams.save(LastLiveStream("1:0:1:OTHER", "Other box", LAST_STREAM_BOUQUET))
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.stream()
        val stream = withTimeout(5_000L) { viewModel.uiState.first { it.stream != null } }.stream

        assertEquals(BOUQUET, stream?.bouquetRef)
    }

    @Test
    fun streamLeavesTheBouquetNullWhenTheChannelIsNotInTheProfileBouquet() = runBlocking<Unit> {
        profiles.current.value!!.setDefaultRefValues(BOUQUET, "Favourites")
        receiver.answer = { request ->
            if (request.requestUrl?.encodedPath == GET_SERVICES) {
                MockResponse().setBody(SERVICES_WITHOUT_THE_CHANNEL)
            } else {
                MockResponse().setBody(loadWebFixture("getcurrent.xml"))
            }
        }
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.stream()
        val stream = withTimeout(5_000L) {
            viewModel.uiState.first { it.stream != null }
        }.stream

        assertNull(stream?.bouquetRef)
    }

    @Test
    fun rejectedZapAndStreamShowsTheReceiverText() = runBlocking<Unit> {
        profiles.current.value!!.zapAndStream = true
        receiver.answer = { request ->
            if (request.requestUrl?.encodedPath == "/web/zap") {
                MockResponse().setBody(simpleResult(false, "No free tuner"))
            } else {
                MockResponse().setBody(loadWebFixture("getcurrent.xml"))
            }
        }
        val viewModel = viewModel()
        viewModel.settled()

        viewModel.stream()
        val state = withTimeout(5_000L) { viewModel.uiState.first { it.userMessage != null } }

        assertEquals(UiText.Raw("No free tuner"), state.userMessage)
        assertNull(state.stream)
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

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = CurrentServiceViewModel(
        handle,
        ReceiverRepository(receiverApis(profiles), profiles),
        profiles,
        lastStreams,
        receiver.services,
        receiver.sessions,
        SettingsRepository(preferences)
    ).also { viewModels += it }

    private suspend fun CurrentServiceViewModel.settled(): CurrentServiceUiState =
        withTimeout(5_000L) { uiState.first { it.ready && !it.refreshing } }

    /** The current service for `/web/getcurrent`, the bouquet for `/web/getservices`. */
    private fun routes(request: RecordedRequest): MockResponse =
        if (request.requestUrl?.encodedPath == GET_SERVICES) {
            MockResponse().setBody(loadWebFixture("getservices.xml"))
        } else {
            MockResponse().setBody(loadWebFixture("getcurrent.xml"))
        }

    private companion object {
        val SAVED = CurrentService(service = Service("1:0:1:1", "Saved box"))
        const val REF = "1:0:1:6DCA:44D:1:C00000:0:0:0:"
        const val BOUQUET = EpgTestReceiver.BOUQUET
        const val LAST_STREAM_BOUQUET = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"last.tv\""
        const val GET_SERVICES = "/web/getservices"
        const val SERVICES_WITHOUT_THE_CHANNEL = "<e2servicelist><e2service>" +
            "<e2servicereference>1:0:1:OTHER:1:C00000:0:0:0:</e2servicereference>" +
            "<e2servicename>Other</e2servicename></e2service></e2servicelist>"
    }
}
