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
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.enigmaClients
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
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
    private val viewModels = mutableListOf<CurrentServiceViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = { MockResponse().setBody(loadWebFixture("getcurrent.xml")) }
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
        assertFalse(state.refreshing)
        Thread.sleep(100)
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
        ReceiverRepository(enigmaClients(profiles), profiles),
        profiles,
        receiver.sessions,
        SettingsRepository(preferences)
    ).also { viewModels += it }

    private suspend fun CurrentServiceViewModel.settled(): CurrentServiceUiState =
        withTimeout(5_000L) { uiState.first { it.ready && !it.refreshing } }

    private companion object {
        val SAVED = CurrentService(service = Service("1:0:1:1", "Saved box"))
    }
}
