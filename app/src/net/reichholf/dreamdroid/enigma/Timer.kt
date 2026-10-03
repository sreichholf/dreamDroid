package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * Typed `/web/timerlist` row (`e2timer`).
 *
 * [allowDuplicate] and [autoAdjust] are OpenWebif's, as `0`/`1`, and null where the box did not
 * report them. Its `timerchange` resets each one that is not sent back (web.py:1084-1089 at
 * e46534f). [vps] is null where the receiver did not report VPS: a Dreambox without the VPS
 * plugin, or a snapshot row stored before dreamDroid kept VPS.
 */
data class Timer(
    val reference: String = "",
    val serviceName: String = "",
    val eit: String = "",
    val name: String = "",
    val description: String = "",
    val descriptionExtended: String = "",
    val disabled: String = "",
    val begin: String = "",
    val end: String = "",
    val duration: String = "",
    val beginReadable: String = "",
    val endReadable: String = "",
    val durationReadable: String = "",
    val startPrepare: String = "",
    val justPlay: String = "",
    val afterEvent: String = "",
    val location: String = "",
    val tags: String = "",
    val logEntries: String = "",
    val fileName: String = "",
    val backOff: String = "",
    val nextActivation: String = "",
    val firstTryPrepare: String = "",
    val state: String = "",
    val repeated: String = "",
    val dontSave: String = "",
    val canceled: String = "",
    val toggleDisabled: String = "",
    val allowDuplicate: String? = null,
    val autoAdjust: String? = null,
    val vps: TimerVps? = null
) : Serializable

/** The VPS choice for a timer, as the receiver offers it: No, Yes (safe mode), Yes. */
enum class VpsMode {
    /** No VPS: the timer records its planned window. */
    Off,

    /** Records the planned window and extends it by what VPS reports. */
    Safe,

    /** VPS controls start and stop. */
    Overwrite
}

/**
 * VPS as the receiver stores it on a timer. [time] is the announced start, in seconds, that
 * the receiver looks for on a timer without an event id or name; null for none.
 */
data class TimerVps(val mode: VpsMode, val time: Long? = null) : Serializable
