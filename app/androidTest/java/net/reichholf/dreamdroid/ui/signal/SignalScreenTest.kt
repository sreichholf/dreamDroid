package net.reichholf.dreamdroid.ui.signal

import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import com.ekndev.gaugelibrary.HalfGauge
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
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
                    onAcousticChange = {},
                )
            }
        }
        composeRule.waitForIdle()
        val gauge = findHalfGauge(composeRule.activity.window.decorView)
        checkNotNull(gauge) { "HalfGauge missing from view tree" }
        val needle = needlePaint(gauge)
        assertNotEquals(Color.BLACK, needle.color)
        assertEquals(onSurfaceArgb, needle.color)
    }
}

private fun findHalfGauge(view: View): HalfGauge? {
    if (view is HalfGauge) {
        return view
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val match = findHalfGauge(view.getChildAt(i))
            if (match != null) {
                return match
            }
        }
    }
    return null
}

private fun needlePaint(gauge: HalfGauge): Paint {
    val superClass = checkNotNull(gauge.javaClass.superclass) {
        "HalfGauge has no superclass"
    }
    val method = superClass.getDeclaredMethod("getNeedlePaint")
    method.isAccessible = true
    return method.invoke(gauge) as Paint
}
