package net.reichholf.dreamdroid.ui.epg

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
    fun datePickerConfirmKeepsUtcMidnightAndContentColorIsOnSurface() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val initial = localSec(berlin, 2026, Calendar.SEPTEMBER, 13, 20, 15)
        var confirmed: Long? = null
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                EpgDatePickerDialog(
                    initialTimeSec = initial,
                    timeZone = berlin,
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Select date").assertIsDisplayed()
        val titleBounds = composeRule.onNodeWithText("Select date").getBoundsInRoot()
        assertTrue(
            "stock DatePicker title should be inset, not clipped in the corner (left=${titleBounds.left})",
            titleBounds.left >= 16.dp,
        )
        composeRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(EpgInstant.utcMidnightMillis(initial, berlin), confirmed)
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }

    @Test
    fun timePickerConfirmKeepsHourAndMinute() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val initial = localSec(berlin, 2026, Calendar.SEPTEMBER, 13, 20, 15)
        var hour: Int? = null
        var minute: Int? = null
        composeRule.setContent {
            DreamDroidTheme {
                EpgTimePickerDialog(
                    initialTimeSec = initial,
                    is24Hour = true,
                    timeZone = berlin,
                    onDismiss = {},
                    onConfirm = { h, m ->
                        hour = h
                        minute = m
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Select hour").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(20, hour)
        assertEquals(15, minute)
    }

    @Test
    fun datePickerCancelDoesNotConfirm() {
        var dismissed = false
        var confirmed: Long? = null
        composeRule.setContent {
            DreamDroidTheme {
                EpgDatePickerDialog(
                    initialTimeSec = 1_789_312_500,
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
