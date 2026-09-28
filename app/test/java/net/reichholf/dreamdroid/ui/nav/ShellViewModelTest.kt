package net.reichholf.dreamdroid.ui.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ShellViewModel] over the real repositories and a MockWebServer receiver. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val viewModels = mutableListOf<ShellViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        receiver.answer = { request ->
            when (request.requestUrl?.encodedPath) {
                "/web/powerstate" -> MockResponse().setBody(
                    "<e2powerstate><e2instandby>true</e2instandby></e2powerstate>"
                )

                "/web/sleeptimer" -> MockResponse().setBody(
                    "<e2sleeptimer><e2enabled>True</e2enabled><e2minutes>30</e2minutes>" +
                        "<e2action>standby</e2action><e2text>In 30 minutes</e2text></e2sleeptimer>"
                )

                "/web/message" -> MockResponse().setBody(simpleResult(true, "Sent"))

                else -> MockResponse().setResponseCode(404)
            }
        }
        receiver.start()
    }

    @AfterEach
    fun tearDown() {
        runBlocking { viewModels.forEach { it.cancelAndJoin() } }
        receiver.stop()
        Dispatchers.resetMain()
    }

    @Test
    fun powerToggleReportsStandby() = runBlocking {
        val viewModel = viewModel()

        viewModel.onPowerMenuAction(Statics.ITEM_TOGGLE_STANDBY)
        val state = viewModel.awaitState { it.userMessage != null }

        assertEquals(UiText.Resource(R.string.in_standby), state.userMessage)
        assertEquals("0", receiver.powerRequest().queryParameter("newstate"))
        viewModel.onMessageShown()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun powerFailureReportsTheContentError() = runBlocking {
        receiver.answer = { MockResponse().setResponseCode(500) }
        val viewModel = viewModel()

        viewModel.setPowerState("0")
        val state = viewModel.awaitState { it.userMessage != null }

        assertEquals(
            EnigmaHttpError(EnigmaFailure.fromHttpStatus(500, "Server Error"))
                .contentErrorText(),
            state.userMessage
        )
    }

    @Test
    fun unknownPowerMenuActionDoesNothing() = runBlocking {
        val viewModel = viewModel()

        viewModel.onPowerMenuAction(-1)

        assertTrue(receiver.requests.isEmpty())
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun sleepTimerReadOpensTheEffect() = runBlocking {
        val viewModel = viewModel()

        viewModel.loadSleepTimerForDialog()
        val state = viewModel.awaitState { it.sleepTimerEffect != null }

        assertEquals("True", state.sleepTimerEffect?.enabled)
        assertNull(state.userMessage)
        viewModel.onSleepTimerEffectHandled()
        assertNull(viewModel.uiState.value.sleepTimerEffect)
    }

    @Test
    fun sleepTimerWriteReportsTheTimerText() = runBlocking {
        val viewModel = viewModel()

        viewModel.setSleepTimer("30", "standby", true)
        val state = viewModel.awaitState { it.userMessage != null }

        assertEquals(UiText.Raw("In 30 minutes"), state.userMessage)
        val request = receiver.requests.single().requestUrl!!
        assertEquals("set", request.queryParameter("cmd"))
        assertEquals("30", request.queryParameter("time"))
    }

    @Test
    fun sendMessageReportsTheReply() = runBlocking {
        val viewModel = viewModel()

        viewModel.sendMessage("Hello", "2", "10")
        val state = viewModel.awaitState { it.userMessage != null }

        assertEquals(UiText.Raw("Sent"), state.userMessage)
    }

    @Test
    fun profileSwitchSurfacesAnEffect() = runBlocking {
        val viewModel = viewModel()
        receiver.profiles.database.profileDao().addProfile(
            Profile().apply {
                id = 8
                name = "other"
                host = "other-box"
                port = 80
            }
        )

        profiles.setCurrent(8)
        val state = viewModel.awaitState { it.profileSwitchEffect != null }

        assertEquals(8, state.profileSwitchEffect?.id)
        viewModel.onProfileSwitchHandled()
        assertNull(viewModel.uiState.value.profileSwitchEffect)
    }

    private fun viewModel(): ShellViewModel = ShellViewModel(
        ReceiverRepository(EnigmaClientFactory(profiles), profiles),
        profiles
    ).also { viewModels += it }

    private fun EpgTestReceiver.powerRequest() = requests.single {
        it.requestUrl?.encodedPath == "/web/powerstate"
    }.requestUrl!!

    private suspend fun ShellViewModel.awaitState(
        condition: (ShellUiState) -> Boolean
    ): ShellUiState = withTimeout(5_000L) { uiState.first(condition) }
}
