package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithText("Activate").assertIsDisplayed().assertIsOn()
        composeRule.onNodeWithText("Standby").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("Shutdown").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithTag(SLEEP_TIMER_MINUTES_TAG)
            .assertIsDisplayed()
            .assertTextContains("90")
        val activate = composeRule.onNode(hasText("Activate") and isToggleable())
            .getBoundsInRoot()
        val activateHeight = activate.bottom - activate.top
        assertTrue(
            "Switch rows are at least 56.dp, height=$activateHeight",
            activateHeight >= 56.dp
        )
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

    @Test
    fun activateSwitchAndActionSegmentStayIndependent() {
        val state = SleepTimerUiState(15, false, SleepTimer.ACTION_STANDBY)
        composeRule.setContent {
            DreamDroidTheme {
                SleepTimerScreen(state = state)
            }
        }
        composeRule.onNodeWithText("Activate").assertIsOff()
        composeRule.onNodeWithText("Shutdown").performClick()
        composeRule.runOnIdle {
            assertEquals(false, state.enabled)
            assertEquals(SleepTimer.ACTION_SHUTDOWN, state.action)
        }
        composeRule.onNodeWithText("Activate").performClick()
        composeRule.runOnIdle { assertEquals(true, state.enabled) }
        composeRule.onNodeWithText("Activate").assertIsOn()
        composeRule.onNodeWithText("Shutdown").assertIsSelected()
        composeRule.onNodeWithText("Standby").assertIsNotSelected()
    }
}
