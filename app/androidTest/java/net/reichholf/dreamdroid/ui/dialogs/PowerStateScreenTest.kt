package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PowerStateScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsPowerChoices() {
        val items = listOf(
            PowerChoiceItem(Statics.ITEM_TOGGLE_STANDBY, "Standby"),
            PowerChoiceItem(Statics.ITEM_RESTART_GUI, "Restart GUI"),
            PowerChoiceItem(Statics.ITEM_REBOOT, "Reboot"),
            PowerChoiceItem(Statics.ITEM_SHUTDOWN, "Shutdown"),
        )
        composeRule.setContent {
            DreamDroidTheme {
                PowerStateScreen(items = items, onItemClick = {})
            }
        }
        composeRule.onNodeWithText("Standby").assertIsDisplayed()
        composeRule.onNodeWithText("Restart GUI").assertIsDisplayed()
        composeRule.onNodeWithText("Reboot").assertIsDisplayed()
        composeRule.onNodeWithText("Shutdown").assertIsDisplayed()
    }
}
