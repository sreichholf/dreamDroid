package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.text.format.DateFormat
import java.util.Date
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python

/**
 * "20:14 – 23:08 · 115 min left" for a running event, in the device's time format; null
 * when the event has no usable start or duration.
 */
fun nowEventTimes(context: Context, event: Event?): String? {
    if (event == null || !hasTime(event.start) || !hasTime(event.duration)) {
        return null
    }
    return try {
        val startMs = event.start.toDouble().toLong() * 1000
        val endMs = startMs + event.duration.toDouble().toLong() * 1000
        val format = DateFormat.getTimeFormat(context)
        val left = DateTime.getRemaining(event.duration, event.start, event.currentTime)
            .coerceAtLeast(0)
        "${format.format(Date(startMs))} – ${format.format(Date(endMs))} · " +
            context.getString(R.string.tv_minutes_left, left)
    } catch (_: NumberFormatException) {
        null
    }
}

private fun hasTime(value: String): Boolean = value.isNotEmpty() && value != Python.NONE
