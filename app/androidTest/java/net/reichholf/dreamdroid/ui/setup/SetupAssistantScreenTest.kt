package net.reichholf.dreamdroid.ui.setup

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileCheckRepository
import net.reichholf.dreamdroid.data.ReceiverDiscovery
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
import net.reichholf.dreamdroid.testutil.enterTouchMode
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SetupAssistantScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun welcomeMovesUpBeforeStartEnables() {
        composeRule.mainClock.autoAdvance = false
        val viewModel = model()
        composeRule.setContent {
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = {},
                    onLeave = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(50)
        composeRule.waitForIdle()
        val centered = composeRule.onNodeWithText("Welcome!").getBoundsInRoot().top.value
        composeRule.onNodeWithText("Start").assertIsNotEnabled()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val welcome = composeRule.onNodeWithText("Welcome!").getBoundsInRoot()
        val raised = welcome.top.value
        val rootBounds = composeRule.onRoot().getBoundsInRoot()
        val rootHeight = rootBounds.bottom.value - rootBounds.top.value
        val logo = composeRule.onNodeWithTag("setup_logo").assertIsDisplayed().getBoundsInRoot()
        val start = composeRule.onNodeWithText("Start").getBoundsInRoot()
        assertTrue(raised < centered)
        assertTrue(logo.bottom.value - logo.top.value >= 240f)
        assertTrue(logo.top.value < rootHeight * 0.2f)
        assertTrue(raised - logo.bottom.value >= 16f)
        assertTrue(start.top.value >= welcome.bottom.value)
        val startCenter = (start.left.value + start.right.value) / 2f
        val rootCenter = (rootBounds.left.value + rootBounds.right.value) / 2f
        val rootWidth = rootBounds.right.value - rootBounds.left.value
        assertTrue(startCenter > rootCenter - rootWidth * 0.05f)
        assertTrue(startCenter < rootCenter + rootWidth * 0.05f)
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val findTitle = composeRule.onNodeWithText("Find your receiver").getBoundsInRoot()
        val logoAfter = composeRule.onNodeWithTag("setup_logo").assertIsDisplayed()
            .getBoundsInRoot()
        assertTrue(logoAfter.top.value - logo.top.value < 4f)
        assertTrue(logo.top.value - logoAfter.top.value < 4f)
        assertTrue(logoAfter.bottom.value <= findTitle.top.value + 2f)
    }

    @Test
    fun findSaysNoneFoundOnlyAfterSearchFinishes() {
        val gate = CompletableDeferred<List<Profile>>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val searching = context.getString(R.string.setup_searching)
        val noneFound = context.getString(R.string.setup_none_found)
        val viewModel = model(onSearch = { gate.await() })
        composeRule.setContent {
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = {},
                    onLeave = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(noneFound).assertDoesNotExist()
        composeRule.onNodeWithText(searching).performScrollTo().assertIsDisplayed()
        gate.complete(emptyList())
        composeRule.waitForIdle()
        composeRule.onNodeWithText(noneFound).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun failedCertificateCheckShowsWarningAndStillSaves() = runBlocking<Unit> {
        var checks = 0
        var finished = false
        val viewModel = model(
            onCheck = { profile ->
                checks += 1
                ProfileCheckResult(
                    hasError = true,
                    errorText = UiText.Raw("certificate"),
                    failure = EnigmaFailure.Unreachable(EnigmaFailure.UnreachableReason.Ssl)
                ).also { savedTrust = profile.allCertsTrusted }
            }
        )
        composeRule.setContent {
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = { finished = true },
                    onLeave = {}
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("setup_address").performTextInput("192.168.1.2")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithTag("setup_port").assert(hasText("80"))
        composeRule.onNodeWithTag("setup_https").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_port").assert(hasText("443"))
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNode(isToggleable()).assertIsOn()
        composeRule.onNodeWithText("Check connection").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("certificate").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("This can be dangerous.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("setup_trust_all").assertIsOff().performClick()
        composeRule.waitForIdle()
        assertTrue(savedTrust)
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { finished }
        val profile = profiles.profiles().singleOrNull()
        assertEquals(profile?.id, profiles.requireCurrent().id)
        assertEquals("192.168.1.2", profile?.host)
        assertEquals("192.168.1.2", profile?.name)
        assertEquals("root", profile?.user)
        assertEquals("dreambox", profile?.pass)
        assertTrue(profile?.login == true)
        assertTrue(profile?.ssl == true)
        assertEquals(443, profile?.port)
        assertTrue(profile?.allCertsTrusted == true)
        assertTrue(checks >= 2)
    }

    @Test
    fun remountKeepsDraftAndInFlightCheckWithoutRerunning() {
        val gate = CompletableDeferred<ProfileCheckResult>()
        var searches = 0
        var checks = 0
        val viewModel = model(
            onSearch = {
                searches += 1
                emptyList()
            },
            onCheck = {
                checks += 1
                gate.await()
            }
        )
        var shown by mutableStateOf(true)
        composeRule.setContent {
            DreamDroidTheme {
                if (shown) {
                    SetupAssistantScreen(
                        viewModel = viewModel,
                        localNetworkGranted = true,
                        onRequestLocalNetwork = {},
                        onFinished = {},
                        onLeave = {}
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_address").performTextInput("192.168.1.2")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Check connection").performClick()
        composeRule.waitForIdle()

        shown = false
        composeRule.waitForIdle()
        shown = true
        composeRule.waitForIdle()

        val state = viewModel.uiState.value
        assertEquals(SetupStep.SignIn, state.draft.step)
        assertEquals("192.168.1.2", viewModel.host.text)
        assertTrue(state.checking)
        gate.complete(ProfileCheckResult())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").assertIsEnabled()
        assertEquals(1, searches)
        assertEquals(1, checks)
    }

    @Test
    fun scanResultsAreVisibleInATvSizedWindow() {
        val device = Profile().apply {
            id = 1
            name = "Living room"
            host = "192.168.1.10"
            port = 80
        }
        val viewModel = model(onSearch = { listOf(device) })
        composeRule.setContent {
            DreamDroidTheme {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.WindowSize(DpSize(960.dp, 540.dp))
                ) {
                    SetupAssistantScreen(
                        viewModel = viewModel,
                        localNetworkGranted = true,
                        onRequestLocalNetwork = {},
                        onFinished = {},
                        onLeave = {},
                        modifier = Modifier.testTag("setup_window")
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("setup_window").getBoundsInRoot()
        val row = composeRule.onNodeWithTag("setup_device_192.168.1.10")
            .assertIsDisplayed()
            .getBoundsInRoot()
        assertTrue(
            "The first scan result must lie within the window, was $row in $root",
            row.top >= root.top && row.bottom <= root.bottom
        )
    }

    @Test
    fun failedCheckScrollsItsErrorIntoATvSizedWindow() {
        val viewModel = model(
            onCheck = {
                ProfileCheckResult(hasError = true, errorText = UiText.Raw("unreachable"))
            }
        )
        composeRule.setContent {
            DreamDroidTheme {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.WindowSize(DpSize(960.dp, 540.dp))
                ) {
                    SetupAssistantScreen(
                        viewModel = viewModel,
                        localNetworkGranted = true,
                        onRequestLocalNetwork = {},
                        onFinished = {},
                        onLeave = {},
                        modifier = Modifier.testTag("setup_window")
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_address").performTextInput("192.168.1.2")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Check connection").performClick()
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("setup_window").getBoundsInRoot()
        val back = composeRule.onNodeWithText("Back").assertIsDisplayed().getBoundsInRoot()
        val error = composeRule.onNodeWithText("unreachable")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .getBoundsInRoot()
        assertTrue(
            "The check error must lie within the window, was $error in $root",
            error.top >= root.top && error.bottom <= root.bottom
        )
        assertTrue(
            "The check error must sit above the button row, was $error, $back",
            error.bottom <= back.top
        )
    }

    @Test
    fun focusedPasswordStaysVisibleWhenTheKeyboardShrinksATvWindow() {
        val viewModel = model()
        var size by mutableStateOf(DpSize(960.dp, 540.dp))
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(size)) {
                    SetupAssistantScreen(
                        viewModel = viewModel,
                        localNetworkGranted = true,
                        onRequestLocalNetwork = {},
                        onFinished = {},
                        onLeave = {},
                        modifier = Modifier.testTag("setup_window")
                    )
                }
            }
        }
        // A remote drives the TV setup; buttons take focus only outside touch mode.
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_address").performTextInput("192.168.1.2")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup_password")
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.onNodeWithTag("setup_password").assertIsFocused()

        // The soft keyboard shrinks the window from below while the field keeps focus.
        size = DpSize(960.dp, 300.dp)
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("setup_window").getBoundsInRoot()
        val back = composeRule.onNodeWithText("Back").assertIsDisplayed().getBoundsInRoot()
        val password = composeRule.onNodeWithTag("setup_password")
            .assertIsFocused()
            .assertIsDisplayed()
            .getBoundsInRoot()
        assertTrue(
            "The focused password field must lie within the window, was $password in $root",
            password.top >= root.top && password.bottom <= root.bottom
        )
        assertTrue(
            "The focused password field must sit above the button row, was $password, $back",
            password.bottom <= back.top
        )
    }

    @Test
    fun checkKeepsFocusOnThePrimaryButtonAndOffTheUserField() {
        val gate = CompletableDeferred<ProfileCheckResult>()
        var checks = 0
        val viewModel = model(
            onCheck = {
                checks += 1
                gate.await()
            }
        )
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = {},
                    onLeave = {}
                )
            }
        }
        // A remote drives the TV setup; buttons take focus only outside touch mode.
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_address").performTextInput("192.168.1.2")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()

        val action = composeRule.onNodeWithText("Check connection")
        action.performSemanticsAction(SemanticsActions.RequestFocus)
        action.assertIsFocused()
        action.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertTrue(viewModel.uiState.value.checking)
        val checkingText = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.checking)
        composeRule.onNodeWithText("Check connection")
            .assertIsEnabled()
            .assertIsFocused()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, checkingText)
            )
        composeRule.onNodeWithTag("setup_user").assertIsNotFocused()

        gate.complete(ProfileCheckResult())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").assertIsEnabled().assertIsFocused()
        composeRule.onNodeWithTag("setup_user").assertIsNotFocused()
        assertEquals(1, checks)
    }

    @Test
    fun remoteFocusEnlargesTheFocusedAction() {
        val viewModel = model()
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = {},
                    onLeave = {}
                )
            }
        }
        // A remote drives the TV setup; buttons take focus only outside touch mode.
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_address").performTextInput("192.168.1.2")
        composeRule.waitForIdle()

        val next = composeRule.onNodeWithText("Next").assertIsNotFocused()
        val back = composeRule.onNodeWithText("Back").assertIsNotFocused()
        val nextIdle = next.size()
        val backIdle = back.size()

        next.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        next.assertIsFocused()
        assertGrown(nextIdle, next.size())
        assertEquals(backIdle, back.size())

        back.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        back.assertIsFocused()
        assertGrown(backIdle, back.size())
        assertEquals(nextIdle, next.size())
    }

    @Test
    fun touchModeKeepsTheActionsAsTheyAre() {
        val viewModel = model()
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = {},
                    onLeave = {}
                )
            }
        }
        composeRule.enterTouchMode(inputModeManager)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").assertIsNotFocused().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_address").performTextInput("192.168.1.2")
        composeRule.waitForIdle()

        val next = composeRule.onNodeWithText("Next")
        val idle = next.size()
        next.performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        next.assertIsNotFocused()
        assertEquals(idle, next.size())
    }

    @Test
    fun remoteStartsOnTheFirstScanResult() {
        val viewModel =
            model(onSearch = { listOf(receiver("192.168.1.10"), receiver("192.168.1.11")) })
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = {},
                    onLeave = {}
                )
            }
        }
        // A remote drives the TV setup; buttons take focus only outside touch mode.
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup_device_192.168.1.10").assertIsFocused()
        composeRule.onNodeWithTag("setup_device_192.168.1.11").assertIsNotFocused()
        composeRule.onNodeWithTag("setup_address").assertIsNotFocused()
    }

    @Test
    fun typingAnAddressKeepsFocusWhenResultsArrive() {
        val gate = CompletableDeferred<List<Profile>>()
        val viewModel = model(onSearch = { gate.await() })
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTheme {
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = {},
                    onLeave = {}
                )
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()
        val address = composeRule.onNodeWithTag("setup_address")
        address.performSemanticsAction(SemanticsActions.RequestFocus)
        address.performTextInput("192.168.1.2")
        composeRule.waitForIdle()

        gate.complete(listOf(receiver("192.168.1.10")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup_device_192.168.1.10").assertIsNotFocused()
        address.assertIsFocused()
    }

    @Test
    fun pickedScanResultIsSelectedInItsOwnColor() {
        val viewModel =
            model(onSearch = { listOf(receiver("192.168.1.10"), receiver("192.168.1.11")) })
        lateinit var inputModeManager: InputModeManager
        lateinit var scheme: ColorScheme
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTheme {
                scheme = MaterialTheme.colorScheme
                SetupAssistantScreen(
                    viewModel = viewModel,
                    localNetworkGranted = true,
                    onRequestLocalNetwork = {},
                    onFinished = {},
                    onLeave = {}
                )
            }
        }
        // In keyboard mode the first result takes focus and shows the focus colour instead.
        composeRule.enterTouchMode(inputModeManager)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()

        val picked = composeRule.onNodeWithTag("setup_device_192.168.1.10")
        val other = composeRule.onNodeWithTag("setup_device_192.168.1.11")
        picked.assertIsNotSelected()
        assertContainer(scheme.surface, other)
        // A semantics click: a real press would draw a ripple over the sampled pixel.
        picked.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        picked.assertIsSelected()
        other.assertIsNotSelected()
        assertContainer(scheme.secondaryContainer, picked)
        assertContainer(scheme.surface, other)
    }

    @Test
    fun recreationKeepsTheStepScrolled() {
        val viewModel = model()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            DreamDroidTheme {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.WindowSize(DpSize(960.dp, 240.dp))
                ) {
                    SetupAssistantScreen(
                        viewModel = viewModel,
                        localNetworkGranted = true,
                        onRequestLocalNetwork = {},
                        onFinished = {},
                        onLeave = {}
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setup_address").performTextInput("192.168.1.2")
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.waitForIdle()

        // The step body is the only vertical scroller on SignIn.
        val body = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        )
        body.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        composeRule.waitForIdle()
        val scrolled = body.scrollOffset()
        assertTrue("The step must scroll in this window, offset was $scrolled", scrolled > 0f)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(scrolled, body.scrollOffset())
    }

    private fun SemanticsNodeInteraction.scrollOffset(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun receiver(host: String) = Profile().apply {
        name = host
        this.host = host
        port = 80
    }

    /** Samples the row's container beside its text, inside the start padding. */
    private fun assertContainer(expected: Color, row: SemanticsNodeInteraction) {
        val pixels = row.captureToImage().toPixelMap()
        val actual = pixels[4, pixels.height / 2]
        val close = listOf(
            expected.red to actual.red,
            expected.green to actual.green,
            expected.blue to actual.blue
        ).all { (want, got) -> abs(want - got) < 0.02f }
        assertTrue("Row container was $actual, expected $expected", close)
    }

    private fun SemanticsNodeInteraction.size(): DpSize {
        val bounds = getBoundsInRoot()
        return DpSize(bounds.right - bounds.left, bounds.bottom - bounds.top)
    }

    private fun assertGrown(idle: DpSize, focused: DpSize) {
        val ratio = focused.width / idle.width
        assertTrue(
            "A focused action must scale up by about 5%, was $idle -> $focused",
            ratio > 1.03f && ratio < 1.07f
        )
        assertTrue(
            "A focused action must scale up by about 5%, was $idle -> $focused",
            focused.height > idle.height
        )
    }

    private val profiles = memoryProfiles()

    private fun model(
        onSearch: suspend () -> List<Profile> = { emptyList() },
        onCheck: suspend (Profile) -> ProfileCheckResult = { ProfileCheckResult() }
    ): SetupAssistantViewModel = SetupAssistantViewModel(
        SavedStateHandle(),
        profiles,
        object : ProfileCheckRepository {
            override suspend fun check(profile: Profile): ProfileCheckResult = onCheck(profile)

            override suspend fun checkReusingDeviceInfo(profile: Profile): ProfileCheckResult =
                onCheck(profile)
        },
        ReceiverDiscovery { onSearch() }
    )

    private var savedTrust: Boolean = false
}
