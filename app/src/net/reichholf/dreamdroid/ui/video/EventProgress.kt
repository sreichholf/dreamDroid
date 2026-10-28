package net.reichholf.dreamdroid.ui.video

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.Python

/** How far a running event is at a given moment, in seconds. */
data class EventProgress(val startSec: Long, val elapsedSec: Long, val totalSec: Long) {
    val leftMinutes: Long
        get() = (totalSec - elapsedSec + 59) / 60
}

/**
 * Progress of [event] at [nowMs] on the device clock. Not the event's `currentTime`: that is
 * the receiver's clock when the EPG was fetched and stays put while the programme runs.
 * Null without a usable start and duration.
 */
fun eventProgress(event: Event?, nowMs: Long): EventProgress? {
    if (event == null || !hasTime(event.start) || !hasTime(event.duration)) {
        return null
    }
    val startSec = event.start.toDoubleOrNull()?.toLong() ?: return null
    val totalSec = event.duration.toDoubleOrNull()?.toLong() ?: return null
    if (totalSec <= 0) {
        return null
    }
    val elapsedSec = (nowMs / 1000 - startSec).coerceIn(0, totalSec)
    return EventProgress(startSec, elapsedSec, totalSec)
}

private fun hasTime(value: String): Boolean = value.isNotEmpty() && value != Python.NONE

/** When to ask the receiver for now/next again. */
internal data class EventReloadSchedule(
    val delayMs: Long,
    /** The ended event the tries belong to; a different one starts the count over. */
    val endedEventKey: String? = null,
    val endedEventTries: Int = 0
)

private const val NO_EVENT_RELOAD_MS: Long = 300_000
private const val ENDED_EVENT_RELOAD_MS: Long = 30_000
private const val ENDED_EVENT_RELOAD_TRIES: Int = 3
private const val EVENT_BOUNDARY_GRACE_MS: Long = 2_000

/**
 * How long to wait before reloading now/next: until the running event ends, and right after
 * the end a few times more, because the receiver's EPG keeps reporting a finished event for
 * a moment. Past [ENDED_EVENT_RELOAD_TRIES] a stale ended event falls back to the plain
 * [NO_EVENT_RELOAD_MS] poll instead of reloading every [ENDED_EVENT_RELOAD_MS].
 *
 * [eventKey] identifies [event] for the tries: an event id alone repeats after a zap to a
 * service that carries the same programme id, so the key also names the service.
 */
internal fun eventReloadSchedule(
    event: Event?,
    eventKey: String?,
    previous: EventReloadSchedule?,
    nowMs: Long
): EventReloadSchedule {
    val progress = eventProgress(event, nowMs) ?: return EventReloadSchedule(NO_EVENT_RELOAD_MS)
    if (progress.elapsedSec < progress.totalSec) {
        return EventReloadSchedule(
            (progress.totalSec - progress.elapsedSec) * 1000 + EVENT_BOUNDARY_GRACE_MS
        )
    }
    val tries =
        if (previous != null && previous.endedEventKey == eventKey) previous.endedEventTries else 0
    return if (tries >= ENDED_EVENT_RELOAD_TRIES) {
        EventReloadSchedule(NO_EVENT_RELOAD_MS, eventKey, tries)
    } else {
        EventReloadSchedule(ENDED_EVENT_RELOAD_MS, eventKey, tries + 1)
    }
}
