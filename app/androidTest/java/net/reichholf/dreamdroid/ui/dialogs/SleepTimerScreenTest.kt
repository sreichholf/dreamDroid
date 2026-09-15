package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SleepTimerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsActivateStandbyShutdown() {
        val state = SleepTimerUiState(90, true, SleepTimer.ACTION_STANDBY)
        composeRule.setContent {
            DreamDroidTheme {
                SleepTimerScreen(state = state)
            }
        }
        composeRule.onNodeWithText("Activate").assertIsDisplayed()
        composeRule.onNodeWithText("Standby").assertIsDisplayed()
        composeRule.onNodeWithText("Shutdown").assertIsDisplayed()
        composeRule.onNodeWithTag(SLEEP_TIMER_MINUTES_TAG)
            .assertIsDisplayed()
            .assertTextContains("90")
    }

    @Test
    fun incrementAndDecrementMinutes() {
        val state = SleepTimerUiState(90, true, SleepTimer.ACTION_STANDBY)
        composeRule.setContent {
            DreamDroidTheme {
                SleepTimerScreen(state = state)
            }
        }
        composeRule.onNodeWithTag(SLEEP_TIMER_MINUTES_INC_TAG).performClick()
        composeRule.runOnIdle { assertEquals(91, state.minutes) }
        composeRule.onNodeWithTag(SLEEP_TIMER_MINUTES_DEC_TAG).performClick()
        composeRule.runOnIdle { assertEquals(90, state.minutes) }
    }
}
