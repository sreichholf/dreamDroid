package net.reichholf.dreamdroid.ui.epg

import androidx.activity.ComponentActivity
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
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
        var container = Color.Unspecified
        var surfaceContainerHigh = Color.Unspecified
        var selectedDay = Color.Unspecified
        var primary = Color.Unspecified
        var headline = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                val scheme = MaterialTheme.colorScheme
                onSurface = scheme.onSurface
                surfaceContainerHigh = scheme.surfaceContainerHigh
                primary = scheme.primary
                val colors = DatePickerDefaults.colors()
                container = colors.containerColor
                selectedDay = colors.selectedDayContainerColor
                headline = colors.headlineContentColor
                EpgDatePickerDialog(
                    initialTimeSec = initial,
                    timeZone = berlin,
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Date").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(EpgInstant.utcMidnightMillis(initial, berlin), confirmed)
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
            assertEquals(surfaceContainerHigh, container)
            assertEquals(primary, selectedDay)
            assertTrue(
                "date picker container should be dark, luminance=${container.luminance()}",
                container.luminance() < 0.4f,
            )
            assertTrue(
                "date picker headline should be light, luminance=${headline.luminance()}",
                headline.luminance() > 0.5f,
            )
            assertNotEquals(Color(0xFFECE6F0), container)
            assertNotEquals(Color(0xFF2B2930), container)
        }
    }

    @Test
    fun timePickerConfirmKeepsHourAndMinute() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val initial = localSec(berlin, 2026, Calendar.SEPTEMBER, 13, 20, 15)
        var hour: Int? = null
        var minute: Int? = null
        var container = Color.Unspecified
        var surfaceContainerHigh = Color.Unspecified
        var clockDial = Color.Unspecified
        var surfaceContainerHighest = Color.Unspecified
        var selector = Color.Unspecified
        var primary = Color.Unspecified
        var unselected = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                val scheme = MaterialTheme.colorScheme
                surfaceContainerHigh = scheme.surfaceContainerHigh
                surfaceContainerHighest = scheme.surfaceContainerHighest
                primary = scheme.primary
                val colors = TimePickerDefaults.colors()
                container = colors.containerColor
                clockDial = colors.clockDialColor
                selector = colors.selectorColor
                unselected = colors.clockDialUnselectedContentColor
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
        composeRule.onNodeWithText("Time").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(20, hour)
        assertEquals(15, minute)
        composeRule.runOnIdle {
            assertEquals(surfaceContainerHigh, container)
            assertEquals(surfaceContainerHighest, clockDial)
            assertEquals(primary, selector)
            assertTrue(
                "time picker container should be dark, luminance=${container.luminance()}",
                container.luminance() < 0.4f,
            )
            assertTrue(
                "clock dial should be dark, luminance=${clockDial.luminance()}",
                clockDial.luminance() < 0.4f,
            )
            assertTrue(
                "clock numbers should be light, luminance=${unselected.luminance()}",
                unselected.luminance() > 0.5f,
            )
            assertNotEquals(Color(0xFFECE6F0), container)
            assertNotEquals(Color(0xFF2B2930), container)
        }
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
