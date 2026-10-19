package net.reichholf.dreamdroid.helpers

import java.net.URLDecoder
import java.net.URLEncoder
import okhttp3.Credentials

/**
 * Credentials in a stream URI. Players and the box (zap to stream) get the stream login only
 * through the URL, so it carries them whenever the profile enables that login, plain http
 * included, as 1.x did. Log URLs through [redact].
 */
object HttpUserInfo {
    private val userInfo = Regex("^([A-Za-z][A-Za-z0-9+.-]*://)([^/?#@]*)@")

    fun embed(enabled: Boolean, user: String?, pass: String?): String {
        if (!enabled) return ""
        return "${encode(user)}:${encode(pass)}@"
    }

    /** [url] with its userinfo masked, for logs. */
    fun redact(url: String): String = userInfo.replaceFirst(url, "$1***@")

    /**
     * [url] without its userinfo, and the Basic `Authorization` header for it; null when the
     * URL carries none. For players that do not send URL credentials themselves (Media3).
     */
    fun toBasicAuth(url: String): Pair<String, String?> {
        val match = userInfo.find(url) ?: return url to null
        val info = match.groupValues[2]
        val header = Credentials.basic(
            decode(info.substringBefore(':')),
            decode(info.substringAfter(':', "")),
            Charsets.UTF_8
        )
        return url.replaceFirst(userInfo, "$1") to header
    }

    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

    private fun encode(value: String?): String =
        URLEncoder.encode(value.orEmpty(), "UTF-8").replace("+", "%20")
}
