package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.userMessage
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShellSnackbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun boxRejectedShowsStateText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rejected = EnigmaFailure.BoxRejected("Timer already exists")
        val result = SimpleResult(state = "False", stateText = rejected.stateText)
        val message = EnigmaResponse(result, EnigmaHttpError(rejected)).userMessage(context)
        composeRule.setContent {
            DreamDroidTheme {
                ShellSnackbarHost()
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            ShellMessages.post(message)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Timer already exists")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Timer already exists").assertIsDisplayed()
    }

    @Test
    fun stoppedHostDoesNotTakeMessages() {
        val stopped = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.CREATED
            }
        }
        composeRule.setContent {
            DreamDroidTheme {
                Column {
                    Box(Modifier.testTag("started")) { ShellSnackbarHost() }
                    CompositionLocalProvider(LocalLifecycleOwner provides stopped) {
                        Box(Modifier.testTag("stopped")) { ShellSnackbarHost() }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { ShellMessages.post("Only the started host") }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Only the started host")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNode(
            hasText("Only the started host") and hasAnyAncestor(hasTestTag("started"))
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText("Only the started host") and hasAnyAncestor(hasTestTag("stopped"))
        ).assertDoesNotExist()
    }
}
