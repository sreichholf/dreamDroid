package net.reichholf.dreamdroid.ui.timers

import android.content.res.Resources
import java.util.Calendar
import java.util.TimeZone
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.TimerVps
import net.reichholf.dreamdroid.enigma.VpsMode
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
    val tags: List<String>,
    /**
     * Null when the field is hidden: no VPS plugin, VPS unknown for the timer, a zap timer or
     * a repeating one.
     */
    val vps: VpsForm? = null
) {
    /** Which weekdays [repeated] names, Monday first. */
    val repeatedDays: BooleanArray
        get() = BooleanArray(REPEATED_BITS.size) { (repeated and REPEATED_BITS[it]) != 0 }

    companion object {
        /**
         * [vpsPlugin] is whether the receiver has the VPS plugin. [name] is the title as typed,
         * which decides whether VPS needs a time.
         */
        fun from(
            timer: Timer,
            locations: List<String>,
            vpsPlugin: Boolean,
            name: CharSequence = timer.name
        ): TimerEditForm = TimerEditForm(
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
            tags = timer.tags.split(" ").filter { it.isNotEmpty() },
            vps = timer.vps?.takeIf { vpsPlugin && timer.allowsVps() }?.let { vps ->
                VpsForm(
                    mode = vps.mode,
                    time = vps.time?.toInt(),
                    manual = timer.isManualForVps(name)
                )
            }
        )
    }
}

/**
 * The VPS choice of a timer. [time] is the announced start in seconds, null while it
 * follows the begin. A [manual] timer has no event id or no name, so the receiver needs
 * that time to find the broadcast.
 */
data class VpsForm(val mode: VpsMode, val time: Int?, val manual: Boolean) {
    /** Whether the VPS time pickers show. */
    val showsTime: Boolean
        get() = manual && mode != VpsMode.Off
}

/** The receiver offers VPS only for record timers that do not repeat. */
private fun Timer.allowsVps(): Boolean =
    justPlay.toIntOrNull() != 1 && (repeated.toIntOrNull() ?: 0) == 0

private fun Timer.isManualForVps(name: CharSequence): Boolean = eit.isBlank() || name.isBlank()

/**
 * [timer] with VPS set to [mode]. A manual timer that turns VPS on without a time starts
 * at its begin, as on the receiver.
 */
internal fun Timer.withVpsMode(mode: VpsMode, name: CharSequence): Timer {
    val current = vps ?: return this
    val time = current.time
        ?: begin.toLongOrNull()?.takeIf { mode != VpsMode.Off && isManualForVps(name) }
    return copy(vps = TimerVps(mode, time))
}

/** [timer] with the VPS time moved to the local day of [utcDateMillis]. */
internal fun Timer.withVpsDate(utcDateMillis: Long): Timer {
    val local = calendarAt(vpsSeconds() ?: return this)
    local.setLocalDay(utcDateMillis)
    return withVpsTime(local)
}

/** [timer] with the VPS time moved to [hourOfDay]:[minute] on the same day. */
internal fun Timer.withVpsClock(hourOfDay: Int, minute: Int): Timer {
    val local = calendarAt(vpsSeconds() ?: return this)
    local.setClock(hourOfDay, minute)
    return withVpsTime(local)
}

/** The VPS time, or the begin it follows while unset; null without VPS. */
private fun Timer.vpsSeconds(): Long? = vps?.let { it.time ?: begin.toLongOrNull() ?: 0L }

private fun Timer.withVpsTime(local: Calendar): Timer {
    val vps = vps ?: return this
    return copy(vps = vps.copy(time = local.timeInMillis / 1000))
}

/**
 * [timer] with the fields the form shows written back the way the receiver expects.
 * A location the receiver does not offer becomes its first one. [vpsPlugin] is whether the
 * receiver has the VPS plugin, so the form shows VPS.
 */
internal fun Timer.normalized(locations: List<String>, vpsPlugin: Boolean): Timer {
    val form = TimerEditForm.from(this, locations, vpsPlugin)
    return copy(
        disabled = if (form.enabled) "0" else "1",
        justPlay = if (form.zap) "1" else "0",
        afterEvent = form.afterEvent.toString(),
        location = locations.getOrNull(form.locationIndex) ?: location,
        vps = vps?.let { normalizedVps(it, form.vps, vpsPlugin) }
    )
}

/**
 * A VPS field hidden for a zap or repeating timer saves as [VpsMode.Off], as the receiver
 * does. A manual timer with VPS on gets the time the form shows. Without the VPS plugin the
 * form shows no VPS, and the timer keeps what the receiver listed.
 */
private fun Timer.normalizedVps(vps: TimerVps, form: VpsForm?, vpsPlugin: Boolean): TimerVps =
    when {
        !vpsPlugin -> vps
        form == null -> TimerVps(VpsMode.Off)
        form.showsTime && vps.time == null -> vps.copy(time = begin.toLongOrNull())
        else -> vps
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
    val local = calendarAt(if (isBegin) begin else end)
    local.setLocalDay(utcDateMillis)
    return withTime(isBegin, local)
}

/** [timer] with the begin or end moved to [hourOfDay]:[minute] on the same day. */
internal fun Timer.withClock(isBegin: Boolean, hourOfDay: Int, minute: Int): Timer {
    val local = calendarAt(if (isBegin) begin else end)
    local.setClock(hourOfDay, minute)
    return withTime(isBegin, local)
}

private fun calendarAt(seconds: String): Calendar = calendarAt(seconds.toLongOrNull() ?: 0L)

private fun calendarAt(seconds: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = seconds * 1000
}

/** Moves this local calendar to the day of the date picker's [utcDateMillis]. */
private fun Calendar.setLocalDay(utcDateMillis: Long) {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = utcDateMillis
    set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
}

private fun Calendar.setClock(hourOfDay: Int, minute: Int) {
    set(Calendar.HOUR_OF_DAY, hourOfDay)
    set(Calendar.MINUTE, minute)
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

    fun onVpsModeChange(mode: VpsMode) {}

    fun onVpsDatePicked(utcDateMillis: Long) {}

    fun onVpsTimePicked(hourOfDay: Int, minute: Int) {}
}
