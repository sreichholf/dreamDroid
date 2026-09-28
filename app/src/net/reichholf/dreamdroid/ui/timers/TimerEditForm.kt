package net.reichholf.dreamdroid.ui.timers

import android.content.res.Resources
import java.util.Calendar
import java.util.TimeZone
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.helpers.enigma2.Timer.Afterevents

/** Weekday bits of `repeated`, Monday first. */
private val REPEATED_BITS = intArrayOf(1, 2, 4, 8, 16, 32, 64)
private const val MONDAY_TO_FRIDAY = 31
private const val DAILY = 127

/**
 * What the editor shows of a timer besides its title and description. Unset or
 * unknown values read as the defaults the receiver gets on save.
 */
data class TimerEditForm(
    val enabled: Boolean,
    val zap: Boolean,
    val begin: Int,
    val end: Int,
    val repeated: Int,
    val serviceName: String,
    val afterEvent: Int,
    val locations: List<String>,
    val locationIndex: Int,
    val tags: List<String>
) {
    /** Which weekdays [repeated] names, Monday first. */
    val repeatedDays: BooleanArray
        get() = BooleanArray(REPEATED_BITS.size) { (repeated and REPEATED_BITS[it]) != 0 }

    companion object {
        fun from(timer: Timer, locations: List<String>): TimerEditForm = TimerEditForm(
            enabled = (timer.disabled.toIntOrNull() ?: 0) == 0,
            zap = timer.justPlay.toIntOrNull() == 1,
            begin = timer.begin.toIntOrNull() ?: 0,
            end = timer.end.toIntOrNull() ?: 0,
            repeated = timer.repeated.toIntOrNull() ?: 0,
            serviceName = timer.serviceName,
            afterEvent = (timer.afterEvent.toIntOrNull() ?: 0)
                .coerceIn(0, Afterevents.entries.size - 1),
            locations = locations,
            locationIndex = locations.indexOf(timer.location).coerceAtLeast(0),
            tags = timer.tags.split(" ").filter { it.isNotEmpty() }
        )
    }
}

/**
 * [timer] with the fields the form shows written back the way the receiver expects.
 * A location the receiver does not offer becomes its first one.
 */
internal fun Timer.normalized(locations: List<String>): Timer {
    val form = TimerEditForm.from(this, locations)
    return copy(
        disabled = if (form.enabled) "0" else "1",
        justPlay = if (form.zap) "1" else "0",
        afterEvent = form.afterEvent.toString(),
        location = locations.getOrNull(form.locationIndex) ?: location
    )
}

/** The `repeated` value for the checked weekday indices, Monday = 0. */
internal fun timerRepeatedValue(days: List<Int>): Int =
    days.filter { it in REPEATED_BITS.indices }.distinct().sumOf { REPEATED_BITS[it] }

/** "Mo, We", "Mo - Fr", "Daily", or "None" for a `repeated` value. */
fun timerRepeatedLabel(resources: Resources, repeated: Int): String {
    when (repeated) {
        MONDAY_TO_FRIDAY -> return resources.getString(R.string.mo_to_fr)
        DAILY -> return resources.getString(R.string.daily)
    }
    val daysShort = resources.getTextArray(R.array.weekdays_short)
    val text = REPEATED_BITS.indices
        .filter { (repeated and REPEATED_BITS[it]) != 0 }
        .joinToString(", ") { daysShort[it].toString() }
    return text.ifEmpty { resources.getString(R.string.none) }
}

/** [timer] with the begin or end moved to the local day of [utcDateMillis]. */
internal fun Timer.withDate(isBegin: Boolean, utcDateMillis: Long): Timer {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = utcDateMillis
    val local = calendarAt(if (isBegin) begin else end)
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    return withTime(isBegin, local)
}

/** [timer] with the begin or end moved to [hourOfDay]:[minute] on the same day. */
internal fun Timer.withClock(isBegin: Boolean, hourOfDay: Int, minute: Int): Timer {
    val local = calendarAt(if (isBegin) begin else end)
    local.set(Calendar.HOUR_OF_DAY, hourOfDay)
    local.set(Calendar.MINUTE, minute)
    return withTime(isBegin, local)
}

private fun calendarAt(seconds: String): Calendar = Calendar.getInstance().apply {
    timeInMillis = (seconds.toLongOrNull() ?: 0L) * 1000
}

private fun Timer.withTime(isBegin: Boolean, local: Calendar): Timer {
    val timestamp = (local.timeInMillis / 1000).toString()
    if (timestamp == (if (isBegin) begin else end)) {
        return this
    }
    val readable = DateTime.getYearDateTimeString(timestamp)
    return if (isBegin) {
        copy(begin = timestamp, beginReadable = readable)
    } else {
        copy(end = timestamp, endReadable = readable)
    }
}

/** Form edits; both timer editors implement them. The defaults ignore the edit. */
interface TimerFormActions {
    fun onEnabledChange(enabled: Boolean) {}

    fun onZapChange(zap: Boolean) {}

    fun onAfterEventChange(index: Int) {}

    fun onLocationChange(index: Int) {}

    /** [days] are the checked weekday indices, Monday = 0. */
    fun onRepeatedChange(days: List<Int>) {}

    /** [indices] point into the receiver's tag list. */
    fun onTagsChange(indices: List<Int>) {}

    fun onDatePicked(isBegin: Boolean, utcDateMillis: Long) {}

    fun onTimePicked(isBegin: Boolean, hourOfDay: Int, minute: Int) {}
}
