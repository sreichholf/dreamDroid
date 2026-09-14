package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.Python

/**
 * Fill [Event.startReadable] / duration strings the same way [EventParser] does.
 * Room round-trips store unix [Event.start] only; format the date line before
 * mapping to EPG detail content.
 */
fun Event.withReadableTimes(): Event {
    if (startReadable.isNotEmpty()) {
        return this
    }
    val startRaw = start.trim()
    val durationRaw = duration.trim()
    if (startRaw.isEmpty() || Python.NONE == startRaw) {
        return this
    }
    val formattedStart = DateTime.getDateTimeString(startRaw)
    val formattedTime = DateTime.getTimeString(startRaw)
    val formattedDuration = try {
        DateTime.getDurationString(durationRaw, startRaw) ?: durationRaw
    } catch (_: NumberFormatException) {
        durationRaw
    }
    return copy(
        startReadable = formattedStart,
        startTimeReadable = formattedTime,
        durationReadable = formattedDuration,
    )
}
