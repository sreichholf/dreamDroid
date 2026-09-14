package net.reichholf.dreamdroid.helpers

/**
 * Credentials in a URI. Cleartext `http://` never carries userinfo — the
 * password would sit on the wire and in logs. HTTPS/RTSP may still embed it.
 */
object HttpUserInfo {
    fun embed(enabled: Boolean, user: String?, pass: String?, scheme: String): String {
        if (!enabled) return ""
        if (scheme.equals("http", ignoreCase = true)) return ""
        return "${user.orEmpty()}:${pass.orEmpty()}@"
    }
}
