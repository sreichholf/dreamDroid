package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfileCheckFailedDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsMessageAndActions() {
        var recheck = false
        var profiles = false
        composeRule.setContent {
            DreamDroidTheme {
                ProfileCheckFailedDialog(
                    title = "user@host:80",
                    message = "Host unreachable",
                    onRecheck = { recheck = true },
                    onProfiles = { profiles = true },
                )
            }
        }
        composeRule.onNodeWithText("user@host:80").assertIsDisplayed()
        composeRule.onNodeWithText("Host unreachable").assertIsDisplayed()
        composeRule.onNodeWithText("Recheck").assertIsDisplayed()
        composeRule.onNodeWithText("Profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Recheck").performClick()
        composeRule.onNodeWithText("Profiles").performClick()
        composeRule.runOnIdle {
            assertTrue(recheck)
            assertTrue(profiles)
        }
    }
}
