package net.reichholf.dreamdroid.helpers

import java.net.URLEncoder

/**
 * Credentials in a stream URI. Players and the box (zap to stream) get the stream login only
 * through the URL, so it carries them whenever the profile enables that login, plain http
 * included, as 1.x did. Log URLs through [redact].
 */
object HttpUserInfo {
    private val userInfo = Regex("^([A-Za-z][A-Za-z0-9+.-]*://)[^/?#@]*@")

    fun embed(enabled: Boolean, user: String?, pass: String?): String {
        if (!enabled) return ""
        return "${encode(user)}:${encode(pass)}@"
    }

    /** [url] with its userinfo masked, for logs. */
    fun redact(url: String): String = userInfo.replaceFirst(url, "$1***@")

    private fun encode(value: String?): String =
        URLEncoder.encode(value.orEmpty(), "UTF-8").replace("+", "%20")
}
