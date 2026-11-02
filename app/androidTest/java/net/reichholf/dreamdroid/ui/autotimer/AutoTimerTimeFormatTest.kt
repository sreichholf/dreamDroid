package net.reichholf.dreamdroid.ui.autotimer

import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AutoTimerTimeFormatTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun formatsAfternoonTimeInTheDeviceHourSetting() {
        assertClockTime(LocalTime.of(13, 5), twentyFourHour = "13:05", twelveHour = "1:05")
    }

    @Test
    fun formatsNoonInTheDeviceHourSetting() {
        assertClockTime(LocalTime.of(12, 0), twentyFourHour = "12:00", twelveHour = "12:00")
    }

    @Test
    fun formatsMidnightInTheDeviceHourSetting() {
        assertClockTime(LocalTime.of(0, 30), twentyFourHour = "00:30", twelveHour = "12:30")
    }

    private fun assertClockTime(time: LocalTime, twentyFourHour: String, twelveHour: String) {
        var formatter: DateTimeFormatter? = null
        composeRule.setContent { formatter = rememberClockFormat() }
        composeRule.waitForIdle()
        val clock = checkNotNull(formatter) { "rememberClockFormat() gave no formatter" }
        val expected = if (is24HourSetting()) {
            twentyFourHour
        } else {
            "$twelveHour ${dayPeriod(time)}"
        }
        assertEquals(
            "24-hour setting=${is24HourSetting()}: expected $expected for $time",
            expected,
            time.format(clock)
        )
    }

    private fun is24HourSetting(): Boolean = DateFormat.is24HourFormat(composeRule.activity)

    private fun dayPeriod(time: LocalTime): String {
        val locale = composeRule.activity.resources.configuration.locales[0]
        return time.format(DateTimeFormatter.ofPattern("a", locale))
    }
}
