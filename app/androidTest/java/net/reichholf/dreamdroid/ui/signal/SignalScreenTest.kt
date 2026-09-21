package net.reichholf.dreamdroid.ui.signal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SignalScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
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
                agcRaw = "73 %"
            ),
            minSnrDb = 5.0
        )
        composeRule.setContent {
            DreamDroidTheme {
                SignalScreen(
                    state = state,
                    onEnabledChange = {},
                    onAcousticChange = {}
                )
            }
        }
        composeRule.onNodeWithText("Enable").assertIsDisplayed()
        composeRule.onNodeWithTag(SIGNAL_SNR_GAUGE_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("SNR 63%").assertIsDisplayed()
        composeRule.onNodeWithText("SNRdb").assertIsDisplayed()
        composeRule.onNodeWithText("12.50 dB").assertIsDisplayed()
        composeRule.onNodeWithText("BER").assertIsDisplayed()
        composeRule.onNodeWithText("AGC").assertIsDisplayed()
        composeRule.onNodeWithText("73 %").assertIsDisplayed()
        composeRule.onNodeWithText("Acoustic Feedback").assertIsDisplayed()
    }

    @Test
    fun nightThemeNeedleIsNotLibraryBlack() {
        val state = SignalUiState()
        var onSurfaceArgb = 0
        composeRule.setContent {
            DreamDroidTheme {
                onSurfaceArgb = MaterialTheme.colorScheme.onSurface.toArgb()
                SignalScreen(
                    state = state,
                    onEnabledChange = {},
                    onAcousticChange = {}
                )
            }
        }
        composeRule.onNodeWithTag(SIGNAL_SNR_GAUGE_TAG).assertIsDisplayed()
        val config = composeRule
            .onNodeWithTag(SIGNAL_SNR_GAUGE_TAG)
            .fetchSemanticsNode()
            .config
        val needleArgb = config[SignalGaugeNeedleColorArgb]
        val valueArgb = config[SignalGaugeValueColorArgb]
        assertNotEquals(Color.Black.toArgb(), needleArgb)
        assertEquals(onSurfaceArgb, needleArgb)
        assertEquals(onSurfaceArgb, valueArgb)
    }
}
