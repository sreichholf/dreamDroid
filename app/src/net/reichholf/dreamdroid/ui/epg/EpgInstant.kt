package net.reichholf.dreamdroid.ui.epg

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Instant math for bouquet EPG time jumps. Material date pickers use UTC midnight
 * millis for the selected calendar day; Enigma2 `/web/epgbouquet` uses local unix
 * seconds.
 */
object EpgInstant {
    const val PRIME_HOUR = 20
    const val PRIME_MINUTE = 15

    fun formatLabel(
        timeSec: Int,
        is24Hour: Boolean,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val date = Date(timeSec * 1000L)
        val dateFmt = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        dateFmt.timeZone = timeZone
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
        val timeFmt = SimpleDateFormat(timePattern, locale)
        timeFmt.timeZone = timeZone
        return "${dateFmt.format(date)} · ${timeFmt.format(date)}"
    }

    fun utcMidnightMillis(
        timeSec: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val local = Calendar.getInstance(timeZone)
        local.timeInMillis = timeSec * 1000L
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.clear()
        utc.set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
        )
        return utc.timeInMillis
    }

    fun combine(
        utcDateMillis: Long,
        hour: Int,
        minute: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Int {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.timeInMillis = utcDateMillis
        val local = Calendar.getInstance(timeZone)
        local.set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            hour,
            minute,
            0,
        )
        local.set(Calendar.MILLISECOND, 0)
        return (local.timeInMillis / 1000).toInt()
    }

    fun primeTimeSec(
        nowSec: Int = (System.currentTimeMillis() / 1000).toInt(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Int {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowSec * 1000L
        cal.set(Calendar.HOUR_OF_DAY, PRIME_HOUR)
        cal.set(Calendar.MINUTE, PRIME_MINUTE)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if ((cal.timeInMillis / 1000) <= nowSec) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (cal.timeInMillis / 1000).toInt()
    }
}
