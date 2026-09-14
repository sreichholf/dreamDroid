package net.reichholf.dreamdroid.multiepg

import java.util.Calendar
import java.util.TimeZone
import net.reichholf.dreamdroid.enigma.Timer

/**
 * GraphMultiEPG `show_record_clocks`: a small clock on programme bars that
 * already have a timer. Record vs zap follows `e2justplay`.
 */
enum class MultiEpgTimerClock {
    Record,
    Zap
}

fun multiEpgTimerClockKey(serviceRef: String, eventId: String, startSec: Long): String {
    val id = eventId.trim()
    return if (id.isNotEmpty()) "$serviceRef|$id" else "$serviceRef|$startSec"
}

fun buildMultiEpgTimerClocks(
    channels: List<MultiEpgChannel>,
    timers: List<Timer>,
    timeZone: TimeZone = TimeZone.getDefault()
): Map<String, MultiEpgTimerClock> {
    if (channels.isEmpty() || timers.isEmpty()) {
        return emptyMap()
    }
    val active = ArrayList<ActiveTimer>(timers.size)
    for (timer in timers) {
        val parsed = parseActiveTimer(timer) ?: continue
        active.add(parsed)
    }
    if (active.isEmpty()) {
        return emptyMap()
    }
    val out = LinkedHashMap<String, MultiEpgTimerClock>()
    for (channel in channels) {
        val serviceKey = timerServiceKey(channel.serviceRef)
        for (bar in channel.bars) {
            val clock = clockForBar(bar, serviceKey, active, timeZone) ?: continue
            out[
                multiEpgTimerClockKey(
                    channel.serviceRef,
                    bar.event.eventId,
                    bar.startSec
                )
            ] = clock
        }
    }
    return out
}

internal data class ActiveTimer(
    val serviceKey: String,
    val beginSec: Long,
    val endSec: Long,
    val repeated: Int,
    val zap: Boolean
)

internal fun timerServiceKey(ref: String): String {
    val base = ref.substringBefore(" FROM ").trim()
    if (base.isEmpty()) {
        return ""
    }
    return base.split(':').take(10).joinToString(":").lowercase()
}

internal fun parseActiveTimer(timer: Timer): ActiveTimer? {
    if (isTimerDisabled(timer.disabled) || isTimerCanceled(timer.canceled)) {
        return null
    }
    val begin = timer.begin.toLongOrNull() ?: return null
    var end = timer.end.toLongOrNull()
        ?: (begin + (timer.duration.toLongOrNull() ?: 0L))
    val zap = timer.justPlay.trim() == "1"
    if (zap && end - begin <= 1L) {
        end = begin + 60L
    }
    val key = timerServiceKey(timer.reference)
    if (key.isEmpty()) {
        return null
    }
    return ActiveTimer(
        serviceKey = key,
        beginSec = begin,
        endSec = end.coerceAtLeast(begin + 1L),
        repeated = timer.repeated.toIntOrNull() ?: 0,
        zap = zap
    )
}

internal fun pythonWeekday(unixSec: Long, timeZone: TimeZone): Int {
    val cal = Calendar.getInstance(timeZone)
    cal.timeInMillis = unixSec * 1000L
    // Calendar.SUNDAY=1 … SATURDAY=7 → Python tm_wday Monday=0.
    return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

private fun isTimerDisabled(value: String): Boolean = value.trim() == "1"

private fun isTimerCanceled(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.equals("true", ignoreCase = true) || trimmed == "1"
}

private fun clockForBar(
    bar: MultiEpgBar,
    serviceKey: String,
    timers: List<ActiveTimer>,
    timeZone: TimeZone
): MultiEpgTimerClock? {
    var zap = false
    var record = false
    for (timer in timers) {
        if (timer.serviceKey != serviceKey) {
            continue
        }
        if (!matchesTime(bar.startSec, bar.endSec, timer, timeZone)) {
            continue
        }
        if (timer.zap) {
            zap = true
        } else {
            record = true
            break
        }
    }
    return when {
        record -> MultiEpgTimerClock.Record
        zap -> MultiEpgTimerClock.Zap
        else -> null
    }
}

private fun matchesTime(
    startSec: Long,
    endSec: Long,
    timer: ActiveTimer,
    timeZone: TimeZone
): Boolean {
    if (timer.repeated == 0) {
        return startSec < timer.endSec && timer.beginSec < endSec
    }
    val eventDay = pythonWeekday(startSec, timeZone)
    if ((timer.repeated and (1 shl eventDay)) == 0) {
        return false
    }
    val eventStartMin = minutesOfDay(startSec, timeZone)
    val eventEndMin = eventStartMin +
        ((endSec - startSec) / 60L).toInt().coerceAtLeast(1)
    val timerStartMin = minutesOfDay(timer.beginSec, timeZone)
    val timerEndMin = timerStartMin +
        ((timer.endSec - timer.beginSec) / 60L).toInt().coerceAtLeast(1)
    return eventStartMin < timerEndMin && timerStartMin < eventEndMin
}

private fun minutesOfDay(unixSec: Long, timeZone: TimeZone): Int {
    val cal = Calendar.getInstance(timeZone)
    cal.timeInMillis = unixSec * 1000L
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}
