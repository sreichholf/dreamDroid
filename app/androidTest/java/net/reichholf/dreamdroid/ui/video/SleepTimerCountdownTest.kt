package net.reichholf.dreamdroid.ui.video

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SleepTimerCountdownTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun countdownShowsSecondsLeftAndExtendIsFocused() {
        var showCountdown by mutableStateOf(false)
        var closing by mutableStateOf(SleepTimer.Closing(10))
        var extends = 0
        var cancels = 0
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTheme(forceDark = true) {
                if (showCountdown) {
                    SleepTimerCountdown(
                        closing = closing,
                        onExtend = { extends++ },
                        onCancel = { cancels++ }
                    )
                }
            }
        }
        // Buttons take focus only outside touch mode. The countdown appears while a remote,
        // not a touchscreen, drives the player.
        composeRule.runOnIdle {
            assertTrue(inputModeManager.requestInputMode(InputMode.Keyboard))
            showCountdown = true
        }

        composeRule.onNodeWithText("Closing the player in 10 s").assertIsDisplayed()
        composeRule.onNodeWithText("+15 min.").assertIsFocused()

        closing = SleepTimer.Closing(3)
        composeRule.onNodeWithText("Closing the player in 3 s").assertIsDisplayed()

        composeRule.onNodeWithText("+15 min.").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(1, extends)
            assertEquals(1, cancels)
        }
    }
}
