package net.reichholf.dreamdroid.ui.timers

import java.util.Calendar
import java.util.TimeZone
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.TimerVps
import net.reichholf.dreamdroid.enigma.VpsMode
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

        val saved = timer.normalized(listOf("/hdd/movie/", "/media/hdd/"), vpsPlugin = true)

        assertEquals("0", saved.disabled)
        assertEquals("0", saved.justPlay)
        assertEquals("0", saved.afterEvent)
        assertEquals("/hdd/movie/", saved.location)
        assertEquals("/gone/", timer.normalized(emptyList(), vpsPlugin = true).location)
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

    @Test
    fun vpsShowsOnlyWithThePluginForRecordTimersThatDoNotRepeat() {
        assertNull(form(EPG_TIMER.copy(vps = null)).vps)
        assertNull(form(EPG_TIMER, vpsPlugin = false).vps)
        assertNull(form(EPG_TIMER.copy(justPlay = "1")).vps)
        assertNull(form(EPG_TIMER.copy(repeated = "31")).vps)

        assertEquals(VpsForm(VpsMode.Safe, time = null, manual = false), form(EPG_TIMER).vps)
    }

    @Test
    fun vpsTimeIsForTimersWithoutEventOrName() {
        val epg = form(EPG_TIMER).vps!!
        assertFalse(epg.manual)
        assertFalse(epg.showsTime)

        val noEvent = form(EPG_TIMER.copy(eit = "")).vps!!
        assertTrue(noEvent.manual)
        assertTrue(noEvent.showsTime)

        assertTrue(TimerEditForm.from(EPG_TIMER, emptyList(), true, name = " ").vps!!.manual)
        assertFalse(form(EPG_TIMER.copy(eit = "", vps = TimerVps(VpsMode.Off))).vps!!.showsTime)
    }

    @Test
    fun normalizedSavesAHiddenVpsFieldAsOff() {
        val zap = EPG_TIMER.copy(justPlay = "1", vps = TimerVps(VpsMode.Overwrite, 500))
        val repeating = EPG_TIMER.copy(repeated = "1")

        assertEquals(TimerVps(VpsMode.Off), zap.normalized(emptyList(), vpsPlugin = true).vps)
        assertEquals(TimerVps(VpsMode.Off), repeating.normalized(emptyList(), vpsPlugin = true).vps)
        assertNull(EPG_TIMER.copy(vps = null).normalized(emptyList(), vpsPlugin = true).vps)
        assertEquals(EPG_TIMER.vps, EPG_TIMER.normalized(emptyList(), vpsPlugin = true).vps)
    }

    @Test
    fun withoutThePluginNormalizedKeepsTheListedVps() {
        val timer = EPG_TIMER.copy(vps = TimerVps(VpsMode.Overwrite, 500))
        val manualZap = EPG_TIMER.copy(eit = "", justPlay = "1")

        assertNull(form(timer, vpsPlugin = false).vps)
        assertEquals(timer.vps, timer.normalized(emptyList(), vpsPlugin = false).vps)
        assertEquals(manualZap.vps, manualZap.normalized(emptyList(), vpsPlugin = false).vps)
    }

    @Test
    fun normalizedGivesAManualVpsTimerItsBegin() {
        val manual = EPG_TIMER.copy(eit = "")

        assertEquals(
            TimerVps(VpsMode.Safe, 1000),
            manual.normalized(emptyList(), vpsPlugin = true).vps
        )
    }

    @Test
    fun turningVpsOnStartsAManualTimerAtItsBegin() {
        val manual = EPG_TIMER.copy(eit = "", vps = TimerVps(VpsMode.Off))

        assertEquals(TimerVps(VpsMode.Safe, 1000), manual.withVpsMode(VpsMode.Safe, "Show").vps)
        assertEquals(
            TimerVps(VpsMode.Overwrite),
            EPG_TIMER.withVpsMode(VpsMode.Overwrite, "Show").vps
        )
        assertNull(EPG_TIMER.copy(vps = null).withVpsMode(VpsMode.Safe, "Show").vps)
    }

    @Test
    fun pickedVpsDateAndClockMoveTheVpsTime() {
        val begin = local(2030, Calendar.JANUARY, 15, 20, 15)
        val timer = EPG_TIMER.copy(eit = "", begin = begin.toString())
        val utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2030, Calendar.MARCH, 2)
        }.timeInMillis

        val moved = timer.withVpsDate(utcDate).withVpsClock(hourOfDay = 20, minute = 10)

        assertEquals(local(2030, Calendar.MARCH, 2, 20, 10), moved.vps?.time)
        assertEquals(timer.begin, moved.begin)
    }

    private fun form(
        timer: Timer,
        locations: List<String> = emptyList(),
        vpsPlugin: Boolean = true
    ) = TimerEditForm.from(timer, locations, vpsPlugin)

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis / 1000

    private companion object {
        val EPG_TIMER = Timer(
            eit = "4711",
            name = "Show",
            begin = "1000",
            end = "2000",
            justPlay = "0",
            repeated = "0",
            vps = TimerVps(VpsMode.Safe)
        )
    }
}
