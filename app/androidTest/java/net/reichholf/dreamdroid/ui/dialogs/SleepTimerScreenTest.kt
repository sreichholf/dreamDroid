package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
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
        val viewModel = sleepTimer(90, true, SleepTimer.ACTION_STANDBY)
        composeRule.setContent {
            DreamDroidTheme {
                SleepTimerHost(viewModel)
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
        val viewModel = sleepTimer(90, true, SleepTimer.ACTION_STANDBY)
        composeRule.setContent {
            DreamDroidTheme {
                SleepTimerHost(viewModel)
            }
        }
        composeRule.onNodeWithTag(SLEEP_TIMER_MINUTES_INC_TAG).performClick()
        composeRule.runOnIdle { assertEquals(91, viewModel.uiState.value.minutes) }
        composeRule.onNodeWithTag(SLEEP_TIMER_MINUTES_DEC_TAG).performClick()
        composeRule.runOnIdle { assertEquals(90, viewModel.uiState.value.minutes) }
    }

    @Test
    fun activateSwitchAndActionSegmentStayIndependent() {
        val viewModel = sleepTimer(15, false, SleepTimer.ACTION_STANDBY)
        composeRule.setContent {
            DreamDroidTheme {
                SleepTimerHost(viewModel)
            }
        }
        composeRule.onNodeWithText("Activate").assertIsOff()
        composeRule.onNodeWithText("Shutdown").performClick()
        composeRule.runOnIdle {
            assertEquals(false, viewModel.uiState.value.enabled)
            assertEquals(SleepTimer.ACTION_SHUTDOWN, viewModel.uiState.value.action)
        }
        composeRule.onNodeWithText("Activate").performClick()
        composeRule.runOnIdle { assertEquals(true, viewModel.uiState.value.enabled) }
        composeRule.onNodeWithText("Activate").assertIsOn()
        composeRule.onNodeWithText("Shutdown").assertIsSelected()
        composeRule.onNodeWithText("Standby").assertIsNotSelected()
    }

    private fun sleepTimer(minutes: Int, enabled: Boolean, action: String) = SleepTimerViewModel(
        SavedStateHandle(mapOf("minutes" to minutes, "enabled" to enabled, "action" to action))
    )

    @Composable
    private fun SleepTimerHost(viewModel: SleepTimerViewModel) {
        val state by viewModel.uiState.collectAsState()
        SleepTimerScreen(
            state = state,
            onMinutesChanged = viewModel::onMinutesChanged,
            onMinutesTyped = viewModel::onMinutesTyped,
            onAdjustMinutes = viewModel::adjustMinutes,
            onEnabledChanged = viewModel::onEnabledChanged,
            onActionSelected = viewModel::onActionSelected
        )
    }
}
