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
