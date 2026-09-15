package net.reichholf.dreamdroid.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Stock Material 3 dialogs / pickers read [MaterialTheme.colorScheme] tokens. Night
 * [DreamDroidTheme] must supply surfaceContainer* and outlineVariant from the app
 * palette so DatePicker / TimePicker / AlertDialog work without per-widget colors=.
 */
@OptIn(ExperimentalMaterial3Api::class)
class DreamDroidThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun stockDialogDefaultsUseDreamDroidSurfacesNotBaselinePurple() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        var primary = Color.Unspecified
        var outlineVariant = Color.Unspecified
        var surfaceContainerHigh = Color.Unspecified
        var surfaceContainerHighest = Color.Unspecified
        var dateContainer = Color.Unspecified
        var dateSelected = Color.Unspecified
        var dateDivider = Color.Unspecified
        var dateHeadline = Color.Unspecified
        var timeContainer = Color.Unspecified
        var timeClock = Color.Unspecified
        var timeSelector = Color.Unspecified
        var timeUnselected = Color.Unspecified
        var alertContainer = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                val scheme = MaterialTheme.colorScheme
                localContent = LocalContentColor.current
                onSurface = scheme.onSurface
                primary = scheme.primary
                outlineVariant = scheme.outlineVariant
                surfaceContainerHigh = scheme.surfaceContainerHigh
                surfaceContainerHighest = scheme.surfaceContainerHighest
                val dateColors = DatePickerDefaults.colors()
                dateContainer = dateColors.containerColor
                dateSelected = dateColors.selectedDayContainerColor
                dateDivider = dateColors.dividerColor
                dateHeadline = dateColors.headlineContentColor
                val timeColors = TimePickerDefaults.colors()
                timeContainer = timeColors.containerColor
                timeClock = timeColors.clockDialColor
                timeSelector = timeColors.selectorColor
                timeUnselected = timeColors.clockDialUnselectedContentColor
                alertContainer = AlertDialogDefaults.containerColor
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f
            )
            assertEquals(primary, dateSelected)
            assertEquals(outlineVariant, dateDivider)
            assertEquals(primary, timeSelector)
            // Current Material 3 TimePickerDefaults uses surfaceContainerHighest
            // for the clock dial (older BOMs used surfaceVariant).
            assertEquals(surfaceContainerHighest, timeClock)
            assertTrue(
                "date picker headline should be light, luminance=${dateHeadline.luminance()}",
                dateHeadline.luminance() > 0.5f
            )
            assertTrue(
                "clock numbers should be light, luminance=${timeUnselected.luminance()}",
                timeUnselected.luminance() > 0.5f
            )
            assertTrue(
                "date picker container should be dark, luminance=${dateContainer.luminance()}",
                dateContainer.luminance() < 0.4f
            )
            assertTrue(
                "time picker container should be dark, luminance=${timeContainer.luminance()}",
                timeContainer.luminance() < 0.4f
            )
            assertTrue(
                "clock dial should be dark, luminance=${timeClock.luminance()}",
                timeClock.luminance() < 0.4f
            )
            assertTrue(
                "alert dialog container should be dark, luminance=${alertContainer.luminance()}",
                alertContainer.luminance() < 0.4f
            )
            assertTrue(
                "surfaceContainerHigh should be dark, " +
                    "luminance=${surfaceContainerHigh.luminance()}",
                surfaceContainerHigh.luminance() < 0.4f
            )
            // Baseline Material purple neutrals that lightColorScheme/darkColorScheme
            // use when surfaceContainer* is omitted.
            assertNotEquals(Color(0xFFECE6F0), surfaceContainerHigh)
            assertNotEquals(Color(0xFF2B2930), surfaceContainerHigh)
            assertNotEquals(Color(0xFFE6E0E9), surfaceContainerHighest)
            assertNotEquals(Color(0xFF36343B), surfaceContainerHighest)
        }
    }
}
