package net.reichholf.dreamdroid.helpers.enigma2

import net.reichholf.dreamdroid.helpers.NameValuePair

object Message {
    const val KEY_TEXT: String = "message"

    fun getParams(text: String?, type: String?, timeout: String?): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("text", text))
        params.add(NameValuePair("type", type))
        params.add(NameValuePair("timeout", timeout))
        return params
    }
}
