package net.reichholf.dreamdroid.ui.timers

import java.util.Calendar
import java.util.TimeZone
import net.reichholf.dreamdroid.enigma.Timer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimerEditFormTest {
    @Test
    fun repeatedBitsStartOnMonday() {
        assertEquals(5, timerRepeatedValue(listOf(0, 2)))
        assertEquals(31, timerRepeatedValue(listOf(0, 1, 2, 3, 4)))
        assertEquals(127, timerRepeatedValue((0..6).toList()))
        assertEquals(1, timerRepeatedValue(listOf(0, 0, 9)))
        val days = form(Timer(repeated = "5")).repeatedDays
        assertArrayEquals(booleanArrayOf(true, false, true, false, false, false, false), days)
    }

    @Test
    fun formReadsTheTimerFields() {
        val form = form(
            Timer(
                disabled = "1",
                justPlay = "1",
                begin = "100",
                end = "200",
                afterEvent = "1",
                location = "/media/hdd/",
                tags = "News  Sport"
            ),
            locations = listOf("/hdd/movie/", "/media/hdd/")
        )

        assertFalse(form.enabled)
        assertTrue(form.zap)
        assertEquals(100, form.begin)
        assertEquals(200, form.end)
        assertEquals(1, form.afterEvent)
        assertEquals(1, form.locationIndex)
        assertEquals(listOf("News", "Sport"), form.tags)
    }

    @Test
    fun unsetFieldsReadAsDefaults() {
        val form = form(Timer(afterEvent = "9"), locations = listOf("/hdd/movie/"))

        assertTrue(form.enabled)
        assertFalse(form.zap)
        assertEquals(3, form.afterEvent)
        assertEquals(0, form.locationIndex)
        assertEquals(emptyList<String>(), form.tags)
    }

    @Test
    fun normalizedWritesWhatTheFormShows() {
        val timer = Timer(disabled = "", justPlay = "", afterEvent = "", location = "/gone/")

        val saved = timer.normalized(listOf("/hdd/movie/", "/media/hdd/"))

        assertEquals("0", saved.disabled)
        assertEquals("0", saved.justPlay)
        assertEquals("0", saved.afterEvent)
        assertEquals("/hdd/movie/", saved.location)
        assertEquals("/gone/", timer.normalized(emptyList()).location)
    }

    @Test
    fun pickedDateKeepsTheTimeOfDay() {
        val begin = local(2030, Calendar.JANUARY, 15, 20, 15)
        val timer = Timer(begin = begin.toString(), end = (begin + 3600).toString())
        val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2030, Calendar.MARCH, 2)
        }.timeInMillis

        val moved = timer.withDate(isBegin = true, utcDateMillis = utcDate)

        assertEquals(local(2030, Calendar.MARCH, 2, 20, 15).toString(), moved.begin)
        assertEquals(timer.end, moved.end)
        assertTrue(moved.beginReadable.isNotEmpty())
    }

    @Test
    fun pickedClockKeepsTheDay() {
        val end = local(2030, Calendar.JANUARY, 15, 21, 0)
        val timer = Timer(begin = "0", end = end.toString())

        val moved = timer.withClock(isBegin = false, hourOfDay = 22, minute = 30)

        assertEquals(local(2030, Calendar.JANUARY, 15, 22, 30).toString(), moved.end)
        assertSame(moved, moved.withClock(isBegin = false, hourOfDay = 22, minute = 30))
    }

    private fun form(timer: Timer, locations: List<String> = emptyList()) =
        TimerEditForm.from(timer, locations)

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis / 1000
}
