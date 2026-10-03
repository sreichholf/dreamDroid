package net.reichholf.dreamdroid.ui.nav

import android.view.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.ReceiverProfileCheckRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.DeviceInfoParser
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.PowerCommand
import net.reichholf.dreamdroid.enigma.WebIfCapabilities
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.testutil.EpgTestReceiver
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import net.reichholf.dreamdroid.testutil.TestReceiver.Companion.simpleResult
import net.reichholf.dreamdroid.testutil.cancelAndJoin
import net.reichholf.dreamdroid.testutil.jobs
import net.reichholf.dreamdroid.testutil.joinJobsSince
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.testutil.receiverApis
import net.reichholf.dreamdroid.ui.profilecheck.ProfileCheckUi
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.text.UiText
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [ShellViewModel] over the real repositories, the real [ReceiverProfileCheckRepository],
 * a MockWebServer receiver, and Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {
    private val receiver = EpgTestReceiver()
    private val profiles = receiver.profiles.repository
    private val capabilities = receiver.profiles.capabilities
    private val clients = receiverApis(profiles, capabilities = capabilities)
    private val sessions = receiver.sessions
    private val preferences = MemorySharedPreferences()
    private val settings = SettingsRepository(preferences)
    private val viewModels = mutableListOf<ShellViewModel>()

    /** `/web/deviceinfo` answers a receiver while true, a non-receiver page otherwise. */
    @Volatile
    private var isReceiver = true

    /** While true, `/web/deviceinfo` answers 401. */
    @Volatile
    private var deviceInfoUnauthorized = false

    /** The `e2webifversion` the receiver's `/web/deviceinfo` reports. */
    @Volatile
    private var webIfVersion = "1.7.4"

    /** Whether `/web/external` lists the AutoTimer plugin. */
    @Volatile
    private var hasAutoTimer = false

    /** While set, `/web/vol` holds its answer until the latch opens. */
    @Volatile
    private var volumeHold: CountDownLatch? = null
    private val volumeArrived = CountDownLatch(1)

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

                "/web/external" -> MockResponse().setBody(
                    "<e2webifexternals><e2webifexternal><e2path>" +
                        (if (hasAutoTimer) "autotimer" else "bouqueteditor") +
                        "</e2path></e2webifexternal></e2webifexternals>"
                )

                "/web/deviceinfo" -> if (deviceInfoUnauthorized) {
                    MockResponse().setResponseCode(401)
                } else {
                    MockResponse().setBody(
                        if (isReceiver) {
                            loadWebFixture("deviceinfo.xml").replace(
                                "<e2webifversion>1.7.4</e2webifversion>",
                                "<e2webifversion>$webIfVersion</e2webifversion>"
                            )
                        } else {
                            "<html>no receiver</html>"
                        }
                    )
                }

                "/web/vol" -> {
                    volumeArrived.countDown()
                    volumeHold?.await(5, TimeUnit.SECONDS)
                    MockResponse().setBody(
                        "<e2volume><e2result>True</e2result><e2current>40</e2current>" +
                            "<e2ismuted>False</e2ismuted></e2volume>"
                    )
                }

                else -> MockResponse().setResponseCode(404)
            }
        }
        receiver.start()
        sessions.resetForProfileChange()
    }

    @AfterEach
    fun tearDown() {
        volumeHold?.countDown()
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

        viewModel.setPowerState(PowerCommand.ToggleStandby)
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
    fun theFirstCheckGoesOnlineAndLeavesTheGateForProfiles() = runBlocking<Unit> {
        val viewModel = viewModel()

        viewModel.checkActiveProfile()
        val state = viewModel.awaitState { it.profileCheckOutcome != null }

        // No cache: the gate shows Checking while the receiver answers.
        assertEquals(ProfileCheckStart(showGate = true), state.profileCheckStarted)
        assertEquals(
            ProfileCheckUi.Checking(UiText.Resource(R.string.checking_connection)),
            state.profileCheck
        )
        assertEquals(
            ProfileCheckOutcome.Leave(offGateToo = true, firstStart = true),
            state.profileCheckOutcome
        )
        assertEquals("test", state.profileName)
        assertEquals(ConnectionStatus.Session.Online, sessions.status.value.session)
        assertEquals(1, deviceInfoRequests())

        viewModel.onProfileCheckStartHandled()
        viewModel.onProfileCheckOutcomeHandled()
        assertNull(viewModel.uiState.value.profileCheckStarted)
        assertNull(viewModel.uiState.value.profileCheckOutcome)
        assertFalse(settings.firstStart)
    }

    @Test
    fun aFailedCheckWithoutCacheOpensTheFailedGate() = runBlocking<Unit> {
        settings.firstStart = false
        isReceiver = false
        val viewModel = viewModel()

        viewModel.checkActiveProfile()
        val state = viewModel.awaitState { it.profileCheckOutcome != null }

        assertEquals(ProfileCheckOutcome.Failed(firstStart = false), state.profileCheckOutcome)
        val failed = assertInstanceOf(ProfileCheckUi.Failed::class.java, state.profileCheck)
        val profile = profiles.requireCurrent()
        assertEquals(UiText.Raw("null@${profile.host}:${profile.port}"), failed.title)
        assertEquals(UiText.Resource(R.string.get_content_error), failed.message)
        assertNull(sessions.status.value.session)
        assertFalse(sessions.status.value.checking)
    }

    @Test
    fun aFailedRequestShowsTheFailuresMessageOnTheGate() = runBlocking<Unit> {
        settings.firstStart = false
        deviceInfoUnauthorized = true
        val viewModel = viewModel()

        viewModel.checkActiveProfile()
        val state = viewModel.awaitState { it.profileCheckOutcome != null }

        val failed = assertInstanceOf(ProfileCheckUi.Failed::class.java, state.profileCheck)
        assertEquals(UiText.Resource(R.string.auth_error), failed.message)
    }

    @Test
    fun aSuccessfulCheckListsAutoTimerInTheDrawerWhenTheReceiverHasThePlugin() = runBlocking<Unit> {
        hasAutoTimer = true
        val viewModel = viewModel()
        assertFalse(viewModel.uiState.value.autoTimerInDrawer)

        viewModel.checkActiveProfile()

        viewModel.awaitState { it.autoTimerInDrawer }
        assertEquals(1, receiver.requestsTo("/web/external").size)
    }

    @Test
    fun aWebInterfaceWithoutTheSleepTimerTakesItOutOfTheDrawer() = runBlocking<Unit> {
        webIfVersion = "1.6.4"
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.sleepTimerInDrawer)

        viewModel.checkActiveProfile()

        viewModel.awaitState { !it.sleepTimerInDrawer }
    }

    @Test
    fun theDrawerOffersTheSleepTimerOfTheActiveProfile() = runBlocking<Unit> {
        val viewModel = viewModel()
        val other = Profile().apply { id = profiles.requireCurrent().id!! + 1 }

        capabilities.set(other, WebIfCapabilities(sleepTimer = false))
        // A rename of the active profile reaches the state after the change above.
        val current = profiles.requireCurrent()
        profiles.setCurrent(
            Profile().apply {
                id = current.id
                name = "Renamed"
                host = current.host
                port = current.port
            }
        )
        viewModel.awaitState { it.profileName == "Renamed" }
        assertTrue(viewModel.uiState.value.sleepTimerInDrawer)

        capabilities.set(profiles.requireCurrent(), WebIfCapabilities(sleepTimer = false))
        viewModel.awaitState { !it.sleepTimerInDrawer }
    }

    @Test
    fun aFailedCheckDoesNotAskForThePlugin() = runBlocking<Unit> {
        hasAutoTimer = true
        isReceiver = false
        val viewModel = viewModel()

        viewModel.checkActiveProfile()
        viewModel.awaitState { it.profileCheckOutcome != null }
        assertFalse(viewModel.uiState.value.autoTimerInDrawer)
        viewModel.onProfileCheckOutcomeHandled()

        // A later check that succeeds asks once; the failed one asked nothing before it.
        isReceiver = true
        viewModel.recheck()
        viewModel.awaitState { it.autoTimerInDrawer }
        assertEquals(1, receiver.requestsTo("/web/external").size)
    }

    @Test
    fun aSuccessfulRecheckAfterAFailureLeavesForTheStartRoute() = runBlocking<Unit> {
        settings.firstStart = false
        isReceiver = false
        val viewModel = viewModel()
        viewModel.checkActiveProfile()
        viewModel.awaitState { it.profileCheckOutcome is ProfileCheckOutcome.Failed }
        viewModel.onProfileCheckOutcomeHandled()

        isReceiver = true
        viewModel.recheck()
        val state = viewModel.awaitState { it.profileCheckOutcome != null }

        assertEquals(
            ProfileCheckOutcome.Leave(offGateToo = true, firstStart = false),
            state.profileCheckOutcome
        )
        assertEquals(2, deviceInfoRequests())
        assertEquals(ConnectionStatus.Session.Online, sessions.status.value.session)
    }

    @Test
    fun aCachedDeviceInfoAnswerIsReusedAndStaysOffTheGate() = runBlocking<Unit> {
        settings.firstStart = false
        profiles.setDeviceInfo(
            profiles.requireCurrent(),
            DeviceInfoParser.parse(loadWebFixture("deviceinfo.xml"))
        )
        val viewModel = viewModel()

        viewModel.checkActiveProfile()
        val state = viewModel.awaitState { it.profileCheckOutcome != null }

        assertEquals(ProfileCheckStart(showGate = false), state.profileCheckStarted)
        assertEquals(
            ProfileCheckOutcome.Leave(offGateToo = false, firstStart = false),
            state.profileCheckOutcome
        )
        assertEquals(0, deviceInfoRequests())
    }

    @Test
    fun anEditedProfileWithCachedDeviceInfoStaysOnlineWithoutChecking() = runBlocking<Unit> {
        settings.firstStart = false
        val viewModel = viewModel()
        viewModel.checkActiveProfile()
        viewModel.awaitState { it.profileCheckOutcome != null }
        viewModel.onProfileCheckOutcomeHandled()
        val checked = profiles.requireCurrent()
        // An edit that keeps the connection settings replaces the instance, not the id.
        profiles.setCurrent(
            Profile().apply {
                id = checked.id
                name = "renamed"
                host = checked.host
                port = checked.port
            }
        )

        viewModel.checkActiveProfile()

        assertFalse(sessions.status.value.checking)
        val state = viewModel.awaitState { it.profileCheckOutcome != null }
        assertEquals(ProfileCheckStart(showGate = false), state.profileCheckStarted)
        assertEquals("renamed", state.profileName)
        assertEquals(ConnectionStatus.Session.Online, sessions.status.value.session)
        assertEquals(1, deviceInfoRequests())
    }

    @Test
    fun switchesAreCheckedOnlyAfterStart() = runBlocking<Unit> {
        settings.firstStart = false
        val viewModel = viewModel()
        val first = profiles.requireCurrent()
        val (setup, next) = listOf("setup", "next").map { profileName ->
            Profile().apply {
                name = profileName
                host = first.host
                port = first.port
            }.also { profiles.save(it) }
        }

        // Before the shell exists, a switch (the setup assistant's) is dropped.
        profiles.setCurrent(setup.id!!, forceEvent = true)
        viewModel.start()
        profiles.setCurrent(next.id!!, forceEvent = true)
        val state = viewModel.awaitState { it.profileCheckOutcome != null }

        assertEquals(
            ProfileCheckOutcome.Leave(offGateToo = false, firstStart = false),
            state.profileCheckOutcome
        )
        assertEquals("next", state.profileName)
        assertEquals(1, deviceInfoRequests())
        assertEquals(ConnectionStatus.Session.Online, sessions.status.value.session)
    }

    @Test
    fun probeRechecksAnOnlineSession() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.checkActiveProfile()
        viewModel.awaitState { it.profileCheckOutcome != null }

        assertTrue(viewModel.probeReachability())

        assertEquals(2, deviceInfoRequests())
        assertEquals(ConnectionStatus.Session.Online, sessions.status.value.session)
    }

    @Test
    fun probeSkipsWithoutASession() = runBlocking<Unit> {
        val viewModel = viewModel()

        assertFalse(viewModel.probeReachability())

        assertEquals(0, deviceInfoRequests())
    }

    @Test
    fun volumeKeysFollowTheSetting() {
        val viewModel = viewModel()
        assertFalse(viewModel.controlsReceiverVolume(KeyEvent.KEYCODE_VOLUME_UP))

        preferences.edit().putBoolean(DreamDroid.PREFS_KEY_VOLUME_CONTROL, true).apply()

        assertTrue(viewModel.controlsReceiverVolume(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(viewModel.controlsReceiverVolume(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(viewModel.controlsReceiverVolume(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun aVolumeKeyWhileARequestRunsIsDropped() = runBlocking<Unit> {
        val hold = CountDownLatch(1)
        volumeHold = hold
        val viewModel = viewModel()
        val before = viewModel.jobs()

        viewModel.onVolumeKey(KeyEvent.KEYCODE_VOLUME_UP)
        assertTrue(volumeArrived.await(5, TimeUnit.SECONDS))
        viewModel.onVolumeKey(KeyEvent.KEYCODE_VOLUME_UP)
        hold.countDown()
        viewModel.joinJobsSince(before)
        assertEquals(listOf("up"), volumeRequests())

        // Once the first request finished, the next key is sent.
        viewModel.onVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN)
        receiver.awaitRequestsTo("/web/vol", 2)

        assertEquals(listOf("up", "down"), volumeRequests())
    }

    private fun viewModel(): ShellViewModel = ShellViewModel(
        ReceiverRepository(clients, profiles),
        AutoTimerRepository(clients, profiles, TestScope()),
        profiles,
        ReceiverProfileCheckRepository(profiles, clients, capabilities),
        receiver.services,
        sessions,
        settings,
        capabilities
    ).also { viewModels += it }

    private fun deviceInfoRequests(): Int = receiver.requestsTo("/web/deviceinfo").size

    private fun volumeRequests(): List<String?> =
        receiver.requestsTo("/web/vol").map { it.requestUrl?.queryParameter("set") }

    private fun EpgTestReceiver.powerRequest() = requests.single {
        it.requestUrl?.encodedPath == "/web/powerstate"
    }.requestUrl!!

    private suspend fun ShellViewModel.awaitState(
        condition: (ShellUiState) -> Boolean
    ): ShellUiState = withTimeout(5_000L) { uiState.first(condition) }
}
