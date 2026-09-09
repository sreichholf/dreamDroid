package net.reichholf.dreamdroid.ui.signal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignalScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsControlsAndMetrics() {
        val state = SignalUiState()
        state.apply(
            Signal(
                snrDbRaw = "12.50 dB",
                snrRaw = "63 %",
                berRaw = "0",
                agcRaw = "73 %",
            ),
            minSnrDb = 5.0,
        )
        composeRule.setContent {
            DreamDroidTheme {
                SignalScreen(
                    state = state,
                    onEnabledChange = {},
                    onAcousticChange = {},
                )
            }
        }
        composeRule.onNodeWithText("Enable").assertIsDisplayed()
        composeRule.onNodeWithText("SNRdb").assertIsDisplayed()
        composeRule.onNodeWithText("12.50 dB").assertIsDisplayed()
        composeRule.onNodeWithText("BER").assertIsDisplayed()
        composeRule.onNodeWithText("AGC").assertIsDisplayed()
        composeRule.onNodeWithText("73 %").assertIsDisplayed()
        composeRule.onNodeWithText("Acoustic Feedback").assertIsDisplayed()
    }
}
