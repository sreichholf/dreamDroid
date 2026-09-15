package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.helpers.NameValuePair

object Message {
    const val KEY_TEXT: String = "message"
    const val KEY_TYPE: String = "type"
    const val KEY_TIMEOUT: String = "timeout"
    const val MESSAGE_TYPE_WARNING: String = "1"
    const val MESSAGE_TYPE_INFO: String = "2"
    const val MESSAGE_TYPE_ERROR: String = "3"

    fun getParams(text: String?, type: String?, timeout: String?): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("text", text))
        params.add(NameValuePair("type", type))
        params.add(NameValuePair("timeout", timeout))
        return params
    }
}
