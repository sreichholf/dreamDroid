package net.reichholf.dreamdroid.helpers

import java.net.URLEncoder

/**
 * Credentials in a URI. Cleartext `http://` never carries userinfo — the
 * password would sit on the wire and in logs. HTTPS/RTSP may still embed it.
 */
object HttpUserInfo {
    private val userInfo = Regex("^([A-Za-z][A-Za-z0-9+.-]*://)[^/?#@]*@")

    fun embed(enabled: Boolean, user: String?, pass: String?, scheme: String): String {
        if (!enabled) return ""
        if (scheme.equals("http", ignoreCase = true)) return ""
        return "${encode(user)}:${encode(pass)}@"
    }

    /** [url] with its userinfo masked, for logs. */
    fun redact(url: String): String = userInfo.replaceFirst(url, "$1***@")

    private fun encode(value: String?): String =
        URLEncoder.encode(value.orEmpty(), "UTF-8").replace("+", "%20")
}
