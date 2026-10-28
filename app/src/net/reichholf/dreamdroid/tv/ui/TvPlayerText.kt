package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Date
import kotlinx.coroutines.delay
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.video.eventProgress

private const val MINUTE_MS = 60_000L

/**
 * "20:14 – 23:08 · 115 min left" for a running event, in the device's time format and at
 * [nowMs] on the device clock; null when the event has no usable start or duration.
 */
fun nowEventTimes(context: Context, event: Event?, nowMs: Long): String? {
    val progress = eventProgress(event, nowMs) ?: return null
    val startMs = progress.startSec * 1000
    val endMs = startMs + progress.totalSec * 1000
    val format = DateFormat.getTimeFormat(context)
    return "${format.format(Date(startMs))} – ${format.format(Date(endMs))} · " +
        context.getString(R.string.tv_minutes_left, progress.leftMinutes.toInt())
}

/**
 * Device-clock milliseconds, refreshed on the next minute boundary: now/next events carry the
 * receiver's `currentTime`, which does not move while the overlay stays open.
 */
@Composable
fun rememberMinuteNowMs(): Long {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
            nowMs = System.currentTimeMillis()
        }
    }
    return nowMs
}
