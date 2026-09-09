package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ConnectionErrorScreenTest {
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
        composeRule.setContent {
            DreamDroidTheme {
                ConnectionErrorScreen(
                    message = "Host unreachable",
                    onPositive = {},
                    onEditProfile = {},
                )
            }
        }
        composeRule.onNodeWithText("Host unreachable").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Edit Profile").assertIsDisplayed()
    }
}
