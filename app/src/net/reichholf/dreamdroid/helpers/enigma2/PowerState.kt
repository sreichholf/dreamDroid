package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.helpers.NameValuePair

object PowerState {
    val KEY_IN_STANDBY: String = "standby"

    val CMD_SET: String = "set"

    val STATE_GET: String = "-1"
    val STATE_TOGGLE: String = "0"
    val STATE_SHUTDOWN: String = "1"
    val STATE_SYSTEM_REBOOT: String = "2"
    val STATE_GUI_RESTART: String = "3"

    fun getStateParams(state: String?): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("newstate", state ?: ""))
        return params
    }
}
