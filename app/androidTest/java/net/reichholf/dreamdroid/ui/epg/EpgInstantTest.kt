package net.reichholf.dreamdroid.ui.epg

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgInstantTest {
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    @Test
    fun formatDateLabelUsesMediumDate() {
        val sec = localSec(2026, Calendar.SEPTEMBER, 13, 20, 15)
        assertEquals(
            "Sep 13, 2026",
            EpgInstant.formatDateLabel(sec, Locale.US, berlin)
        )
    }

    @Test
    fun formatTimeLabelRespects24HourClock() {
        val sec = localSec(2026, Calendar.SEPTEMBER, 13, 20, 15)
        assertEquals("20:15", EpgInstant.formatTimeLabel(sec, true, Locale.US, berlin))
        assertEquals("8:15 PM", EpgInstant.formatTimeLabel(sec, false, Locale.US, berlin))
    }

    @Test
    fun utcMidnightRoundTripPreservesLocalDateAndTime() {
        val sec = localSec(2026, Calendar.SEPTEMBER, 13, 20, 15)
        val utcMidnight = EpgInstant.utcMidnightMillis(sec, berlin)
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = utcMidnight
        assertEquals(2026, utc.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, utc.get(Calendar.MONTH))
        assertEquals(13, utc.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, utc.get(Calendar.HOUR_OF_DAY))
        assertEquals(sec, EpgInstant.combine(utcMidnight, 20, 15, berlin))
    }

    @Test
    fun applyDateKeepsLocalTime() {
        val sec = localSec(2026, Calendar.SEPTEMBER, 13, 20, 15)
        val nextDay = EpgInstant.utcMidnightMillis(
            localSec(2026, Calendar.SEPTEMBER, 14, 8, 0),
            berlin
        )
        assertEquals(
            localSec(2026, Calendar.SEPTEMBER, 14, 20, 15),
            EpgInstant.applyDate(sec, nextDay, berlin)
        )
    }

    @Test
    fun applyTimeKeepsLocalDate() {
        val sec = localSec(2026, Calendar.SEPTEMBER, 13, 20, 15)
        assertEquals(
            localSec(2026, Calendar.SEPTEMBER, 13, 18, 0),
            EpgInstant.applyTime(sec, 18, 0, berlin)
        )
    }

    @Test
    fun primeTimeStaysTodayWhenStillAhead() {
        val now = localSec(2026, Calendar.SEPTEMBER, 13, 18, 0)
        val prime = EpgInstant.primeTimeSec(now, berlin)
        assertEquals(localSec(2026, Calendar.SEPTEMBER, 13, 20, 15), prime)
    }

    @Test
    fun primeTimeMovesToTomorrowWhenAlreadyPast() {
        val now = localSec(2026, Calendar.SEPTEMBER, 13, 20, 15)
        val prime = EpgInstant.primeTimeSec(now, berlin)
        assertEquals(localSec(2026, Calendar.SEPTEMBER, 14, 20, 15), prime)
        assertTrue(prime > now)
    }

    private fun localSec(year: Int, month: Int, day: Int, hour: Int, minute: Int): Int {
        val cal = Calendar.getInstance(berlin)
        cal.clear()
        cal.set(year, month, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return (cal.timeInMillis / 1000).toInt()
    }
}
