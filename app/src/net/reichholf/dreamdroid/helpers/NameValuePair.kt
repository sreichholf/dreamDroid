package net.reichholf.dreamdroid.helpers

import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class NameValuePair(val key: String, private val rawValue: String?) {
    fun key(): String = key

    fun value(): String = rawValue ?: ""

    companion object {
        fun toString(pair: NameValuePair): String {
            var value = ""
            try {
                value = URLEncoder.encode(pair.value(), "utf-8").replace("+", "%20")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
            return String.format("%s=%s", pair.key(), value)
        }

        fun toString(pairs: List<NameValuePair>): String {
            if (pairs.isEmpty()) return ""
            val params = ArrayList<String>(pairs.size)
            for (pair in pairs) {
                params.add(toString(pair))
            }
            return params.joinToString("&")
        }
    }
}
