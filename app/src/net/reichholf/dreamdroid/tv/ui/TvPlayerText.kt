package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.text.format.DateFormat
import java.util.Date
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.video.eventProgress

/**
 * "20:14 – 23:08 · 115 min left" for a running event, in the device's time format and at
 * [nowMs] on the device clock; null when the event has no usable start or duration.
 */
fun nowEventTimes(
    context: Context,
    event: Event?,
    nowMs: Long = System.currentTimeMillis()
): String? {
    val progress = eventProgress(event, nowMs) ?: return null
    val startMs = progress.startSec * 1000
    val endMs = startMs + progress.totalSec * 1000
    val format = DateFormat.getTimeFormat(context)
    return "${format.format(Date(startMs))} – ${format.format(Date(endMs))} · " +
        context.getString(R.string.tv_minutes_left, progress.leftMinutes.toInt())
}
