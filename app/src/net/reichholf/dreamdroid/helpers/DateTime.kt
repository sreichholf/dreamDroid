/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers

import android.util.Log
import net.reichholf.dreamdroid.DreamDroid
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Provides static methods for Date and Time parsing
 *
 * @author sreichholf
 */
object DateTime {
    @JvmStatic
    fun getRemaining(duration: String?, eventstart: String?): Int {
        return getRemaining(duration, eventstart, null)
    }

    @JvmStatic
    fun getRemaining(duration: String?, eventstart: String?, nowTime: String?): Int {
        if (duration == null || Python.NONE == duration) {
            return 0
        }

        var d = duration.toDouble().toLong()

        if (eventstart != null && Python.NONE != eventstart) {
            try {
                val s = eventstart.toDouble().toLong() * 1000
                var now: Date? = null
                if (nowTime != null) {
                    now = getDate(nowTime)
                }
                if (now == null) {
                    now = Date()
                }
                if (now.time >= s) {
                    d = d - ((now.time - s) / 1000)
                    if (d <= 60) {
                        d = 60
                    }
                }
            } catch (nfe: NumberFormatException) {
                Log.e(DreamDroid.LOG_TAG, nfe.message ?: "")
                return 0
            }
        }

        d = d / 60
        return d.toInt()
    }

    @JvmStatic
    fun getDurationString(duration: String?, eventstart: String?): String? {
        if (duration == null || Python.NONE == duration) {
            return "0"
        }

        var d = duration.toDouble().toLong()
        var durationPrefix = ""

        if (eventstart != null && Python.NONE != eventstart) {
            try {
                val s = eventstart.toDouble().toLong() * 1000
                val now = Date()

                if (now.time >= s) {
                    d = d - ((now.time - s) / 1000)
                    if (d <= 60) {
                        d = 60
                    }
                    durationPrefix = "+"
                }
            } catch (nfe: NumberFormatException) {
                Log.e(DreamDroid.LOG_TAG, nfe.message ?: "")
                return duration
            }
        }

        d = d / 60
        return durationPrefix + d
    }

    @JvmStatic
    fun getDateTimeString(timestamp: String): String {
        val sdfDateTime = if (DreamDroid.DATE_LOCALE_WO) {
            SimpleDateFormat("E, dd.MM. - HH:mm", Locale.US)
        } else {
            SimpleDateFormat("E, dd.MM. - HH:mm")
        }

        return getFormattedDateString(sdfDateTime, timestamp)
    }

    @JvmStatic
    fun getYearDateTimeString(timestamp: Long): String {
        return getYearDateTimeString(timestamp.toString())
    }

    @JvmStatic
    fun getYearDateTimeString(timestamp: String): String {
        val sdfDateTime = if (DreamDroid.DATE_LOCALE_WO) {
            SimpleDateFormat("E, dd.MM.yyyy - HH:mm", Locale.US)
        } else {
            SimpleDateFormat("E, dd.MM.yyyy - HH:mm")
        }

        return getFormattedDateString(sdfDateTime, timestamp)
    }

    @JvmStatic
    fun getTimeString(timestamp: String): String {
        val sdfTime = SimpleDateFormat("HH:mm")
        return getFormattedDateString(sdfTime, timestamp)
    }

    @JvmStatic
    fun getDate(timestamp: String): Date? {
        return try {
            var s = timestamp.toDouble().toLong()
            s = s * 1000
            Date(s)
        } catch (_: NumberFormatException) {
            null
        }
    }

    @JvmStatic
    fun getFormattedDateString(sdf: SimpleDateFormat, timestamp: String): String {
        val date = getDate(timestamp)
        if (date != null) {
            return sdf.format(date)
        }
        return "-"
    }

    @JvmStatic
    fun parseTimestamp(timestamp: String?): Int {
        return BigDecimal(timestamp).toInt()
    }

    @JvmStatic
    fun minutesAndSeconds(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", min, sec)
    }

    @JvmStatic
    fun getPrimeTimestamp(): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 20)
        cal.set(Calendar.MINUTE, 15)
        cal.set(Calendar.SECOND, 0)
        return (cal.timeInMillis / 1000).toInt()
    }
}
