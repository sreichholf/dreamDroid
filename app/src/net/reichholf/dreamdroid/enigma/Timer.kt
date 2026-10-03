package net.reichholf.dreamdroid.enigma

import java.io.Serializable

/**
 * Typed `/web/timerlist` row (`e2timer`).
 *
 * [allowDuplicate], [autoAdjust], [vpsEnabled], [vpsOverwrite] and [vpsTime] are OpenWebif's,
 * as `0`/`1` (`vpsTime` in seconds), and null where the box did not report them. Its
 * `timerchange` resets each one that is not sent back (web.py:985-1011,1084-1089 at e46534f).
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
    val vpsEnabled: String? = null,
    val vpsOverwrite: String? = null,
    val vpsTime: String? = null
) : Serializable
