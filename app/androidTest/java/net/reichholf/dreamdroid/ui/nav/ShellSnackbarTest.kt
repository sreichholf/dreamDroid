package net.reichholf.dreamdroid.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import net.reichholf.dreamdroid.enigma.SimpleResult
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
        val message = mutationResultText(
            stateText = result.stateText,
            errorText = EnigmaHttpError(rejected).resolve(context),
            fallback = context.getString(R.string.get_content_error)
        )
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
}
