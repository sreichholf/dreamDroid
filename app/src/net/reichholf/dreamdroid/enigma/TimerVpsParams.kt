package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.NameValuePair

/**
 * The VPS params of a timer write, as both web interfaces read them: the Dreambox VPS plugin's
 * `/vpsplugin/web/...` (`Vps.py` `editTimer`, `addTimerByEventID`) and OpenWebif's `vpsparams`
 * (web.py:981-1011 at e46534f). Both turn VPS off for a missing one, so all three always go.
 * A time of `-1` is none; a time only goes with VPS on.
 */
internal fun TimerVps.params(): List<NameValuePair> {
    val time = time?.takeIf { mode != VpsMode.Off } ?: NO_VPS_TIME
    return listOf(
        NameValuePair("vpsplugin_enabled", if (mode == VpsMode.Off) "0" else "1"),
        NameValuePair("vpsplugin_overwrite", if (mode == VpsMode.Overwrite) "1" else "0"),
        NameValuePair("vpsplugin_time", time.toString())
    )
}

private const val NO_VPS_TIME = -1L
