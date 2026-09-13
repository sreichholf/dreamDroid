package net.reichholf.dreamdroid.ui.epg

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EpgDateTimePickerDialogHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun confirmKeepsInitialInstantAndContentColorIsOnSurface() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val initial = localSec(berlin, 2026, Calendar.SEPTEMBER, 13, 20, 15)
        var confirmed: Int? = null
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                EpgDateTimePickerDialog(
                    initialTimeSec = initial,
                    is24Hour = true,
                    timeZone = berlin,
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(EPG_DATE_TIME_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Date and time").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(initial, confirmed)
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }

    @Test
    fun cancelDoesNotConfirm() {
        var dismissed = false
        var confirmed: Int? = null
        composeRule.setContent {
            DreamDroidTheme {
                EpgDateTimePickerDialog(
                    initialTimeSec = 1_789_312_500,
                    is24Hour = true,
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(true, dismissed)
        assertEquals(null, confirmed)
    }

    private fun localSec(
        timeZone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Int {
        val cal = Calendar.getInstance(timeZone)
        cal.clear()
        cal.set(year, month, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return (cal.timeInMillis / 1000).toInt()
    }
}
