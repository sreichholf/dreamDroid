package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.helpers.NameValuePair

object PowerState {
    const val KEY_IN_STANDBY: String = "standby"

    const val CMD_SET: String = "set"

    const val STATE_GET: String = "-1"
    const val STATE_TOGGLE: String = "0"
    const val STATE_SHUTDOWN: String = "1"
    const val STATE_SYSTEM_REBOOT: String = "2"
    const val STATE_GUI_RESTART: String = "3"

    fun getStateParams(state: String?): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("newstate", state ?: ""))
        return params
    }
}
