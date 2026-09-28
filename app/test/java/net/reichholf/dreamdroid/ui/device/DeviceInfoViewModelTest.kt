package net.reichholf.dreamdroid.ui.device

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [DeviceInfoViewModel] over the real [ReceiverRepository] and a [MockWebServer] receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceInfoViewModelTest {
    private val server = MockWebServer()
    private lateinit var profiles: ProfileRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
        // Same instance as the app's transitional Hilt binding: EnigmaHttp still reads
        // ProfileRepository.get() for the XML dump flag.
        profiles = ProfileRepository.install(TestContext())
        profiles.setCurrent(
            Profile().apply {
                id = 1
                name = "test"
                host = server.hostName
                port = server.port
            }
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        profiles.clearCurrent()
        Dispatchers.resetMain()
    }

    @Test
    fun loadsDeviceInfoFromReceiver() = runTest {
        server.enqueue(MockResponse().setBody(loadWebFixture("deviceinfo.xml")))
        val handle = SavedStateHandle()

        val state = viewModel(handle).settled()

        assertEquals("Solo4K", state.info?.deviceName)
        assertFalse(state.loading)
        assertNull(state.userMessage)
        assertEquals(UiText.Resource(R.string.device_info), state.title)
        assertEquals("/web/deviceinfo", server.takeRequest().requestUrl?.encodedPath)
        assertEquals(state.info, handle.get<DeviceInfo>("device_info"))
    }

    @Test
    fun unparsableResponseSetsUserMessageUntilShown() = runTest {
        server.enqueue(MockResponse().setBody("<html>not a receiver</html>"))
        val viewModel = viewModel(SavedStateHandle())

        val state = viewModel.settled()

        assertNull(state.info)
        assertFalse(state.loading)
        assertEquals(UiText.Resource(R.string.error_parsing), state.userMessage)
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun httpErrorSetsFailureMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val state = viewModel(SavedStateHandle()).settled()

        assertNull(state.info)
        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error")).contentErrorText(),
            state.userMessage
        )
    }

    @Test
    fun failedRefreshKeepsShownInfo() = runTest {
        server.enqueue(MockResponse().setBody(loadWebFixture("deviceinfo.xml")))
        server.enqueue(MockResponse().setBody("<html>not a receiver</html>"))
        val viewModel = viewModel(SavedStateHandle())
        viewModel.settled()

        viewModel.refresh()
        val state = viewModel.settled()

        assertEquals("Solo4K", state.info?.deviceName)
        assertEquals(UiText.Resource(R.string.error_parsing), state.userMessage)
    }

    @Test
    fun restoresSavedInfoWithoutRequest() = runTest {
        val saved = DeviceInfo(guiVersion = "2016-07-28", deviceName = "Solo4K")

        val state = viewModel(SavedStateHandle(mapOf("device_info" to saved))).uiState.value

        assertEquals(saved, state.info)
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun emptySavedInfoStillLoads() = runTest {
        server.enqueue(MockResponse().setBody(loadWebFixture("deviceinfo.xml")))

        val state = viewModel(SavedStateHandle(mapOf("device_info" to DeviceInfo()))).settled()

        assertEquals("Solo4K", state.info?.deviceName)
        assertEquals(1, server.requestCount)
    }

    private fun viewModel(handle: SavedStateHandle) =
        DeviceInfoViewModel(handle, ReceiverRepository(EnigmaClientFactory(profiles)))

    private suspend fun DeviceInfoViewModel.settled(): DeviceInfoUiState =
        uiState.first { !it.refreshing }
}

private class TestContext : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
}
