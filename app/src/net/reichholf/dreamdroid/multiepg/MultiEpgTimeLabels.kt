package net.reichholf.dreamdroid.multiepg

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Visible-day label for the MultiEPG toolbar (local calendar, not UTC chunk). */
object MultiEpgTimeLabels {
    /** The local calendar day of [sec]; a day label keyed on it does not change while panning. */
    fun localDayKey(sec: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(timeZone)
        calendar.timeInMillis = sec * 1000L
        return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
    }

    fun sameLocalDay(aSec: Long, bSec: Long, timeZone: TimeZone = TimeZone.getDefault()): Boolean =
        localDayKey(aSec, timeZone) == localDayKey(bSec, timeZone)

    /**
     * [todayLabel] and the date of today's local day, only the date for any other day.
     * [short] leaves the year out of both and abbreviates the weekday ("Fri, Oct 24"), for a
     * toolbar whose EPG reaches about a week ahead.
     */
    fun formatVisibleDay(
        visibleSec: Long,
        nowSec: Long,
        todayLabel: String,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
        short: Boolean = false
    ): String {
        val today = sameLocalDay(visibleSec, nowSec, timeZone)
        val date = Date(visibleSec * 1000L)
        val day = if (short) {
            val skeleton = if (today) "MMMd" else "EEEMMMd"
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
            SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }.format(date)
        } else {
            val style = if (today) DateFormat.MEDIUM else DateFormat.FULL
            DateFormat.getDateInstance(style, locale).apply { this.timeZone = timeZone }
                .format(date)
        }
        return if (today) "$todayLabel · $day" else day
    }
}
