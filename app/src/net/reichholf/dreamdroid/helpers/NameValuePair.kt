package net.reichholf.dreamdroid.helpers

import android.text.TextUtils
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

class NameValuePair(key: String, value: String?) {
    private val mKey: String = key
    private val mValue: String? = value

    fun key(): String = mKey

    fun value(): String = mValue ?: ""

    companion object {
        @JvmStatic
        fun toString(pair: NameValuePair): String {
            var value = ""
            try {
                value = URLEncoder.encode(pair.value(), "utf-8").replace("+", "%20")
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }
            return String.format("%s=%s", pair.key(), value)
        }

        @JvmStatic
        fun toString(pairs: List<NameValuePair>): String {
            val params = ArrayList<String>()
            for (pair in pairs) {
                params.add(toString(pair))
            }
            return TextUtils.join("&", params)
        }
    }
}
