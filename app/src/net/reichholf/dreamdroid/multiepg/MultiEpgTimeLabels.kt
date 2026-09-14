package net.reichholf.dreamdroid.multiepg

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Visible-day label for the MultiEPG toolbar (local calendar, not UTC chunk). */
object MultiEpgTimeLabels {
    fun sameLocalDay(aSec: Long, bSec: Long, timeZone: TimeZone = TimeZone.getDefault()): Boolean {
        val a = Calendar.getInstance(timeZone)
        a.timeInMillis = aSec * 1000L
        val b = Calendar.getInstance(timeZone)
        b.timeInMillis = bSec * 1000L
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    fun formatVisibleDay(
        visibleSec: Long,
        nowSec: Long,
        todayLabel: String,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val date = Date(visibleSec * 1000L)
        if (sameLocalDay(visibleSec, nowSec, timeZone)) {
            val medium = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
            medium.timeZone = timeZone
            return "$todayLabel · ${medium.format(date)}"
        }
        val full = DateFormat.getDateInstance(DateFormat.FULL, locale)
        full.timeZone = timeZone
        return full.format(date)
    }
}
