package net.reichholf.dreamdroid.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.ProfileCheckResult
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
                    onSave = {},
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
        val gate = CompletableDeferred<List<SetupReceiver>>()
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
                    onSave = {},
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
    fun failedCertificateCheckShowsWarningAndStillSaves() {
        var checks = 0
        var saved: Profile? = null
        val viewModel = model(
            onCheck = { profile ->
                checks += 1
                ProfileCheckResult(
                    hasError = true,
                    errorTextExt = "certificate",
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
                    onSave = { saved = it },
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
        val profile = saved
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
                        onSave = {},
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

        assertEquals(SetupStep.SignIn, viewModel.draft.step)
        assertEquals("192.168.1.2", viewModel.draft.host)
        assertTrue(viewModel.checking)
        gate.complete(ProfileCheckResult())
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Next").assertIsEnabled()
        assertEquals(1, searches)
        assertEquals(1, checks)
    }

    private fun model(
        onSearch: suspend () -> List<SetupReceiver> = { emptyList() },
        onCheck: suspend (Profile) -> ProfileCheckResult = { ProfileCheckResult() }
    ): SetupAssistantViewModel = SetupAssistantViewModel(
        ApplicationProvider.getApplicationContext(),
        SavedStateHandle(),
        object : SetupAssistantBackend {
            override suspend fun search(): List<SetupReceiver> = onSearch()

            override suspend fun check(profile: Profile): ProfileCheckResult = onCheck(profile)
        }
    )

    private var savedTrust: Boolean = false
}
