package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.helpers.NameValuePair

object PowerState {
    @JvmField
    val KEY_IN_STANDBY: String = "standby"

    @JvmField
    val CMD_SET: String = "set"

    @JvmField
    val STATE_GET: String = "-1"
    @JvmField
    val STATE_TOGGLE: String = "0"
    @JvmField
    val STATE_SHUTDOWN: String = "1"
    @JvmField
    val STATE_SYSTEM_REBOOT: String = "2"
    @JvmField
    val STATE_GUI_RESTART: String = "3"

    @JvmStatic
    fun getStateParams(state: String?): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("newstate", state ?: ""))
        return params
    }
}
