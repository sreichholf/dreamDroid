package net.reichholf.dreamdroid.helpers

import net.reichholf.dreamdroid.Profile

/**
 * True when [host] can go into a URL authority unchanged.
 *
 * The URL builders append `:port` to the profile's host, so a host that carries `@`, `/`,
 * `?`, `#` or `\` ends the authority early: `receiver.local@attacker.example` turns the
 * receiver into userinfo and sends the request, including its Basic auth, to
 * `attacker.example`. Whitespace is refused because a URL cannot carry it unencoded. `:`
 * stays allowed for a bracketed IPv6 literal such as `[fe80::1]`.
 */
fun isValidHost(host: String?): Boolean {
    if (host.isNullOrBlank()) {
        return false
    }
    return host.none { it.isWhitespace() || it in ILLEGAL_HOST_CHARS }
}

/**
 * Moves a 1.x `user@host` out of [Profile.host] and [Profile.streamHost], where 2.0 keeps
 * the login. 1.x built `scheme://<host>:<port>` from the host field, so the part before the
 * `@` was URL userinfo and the request went to the part after it. 2.0 refuses `@` in a
 * host, so a profile imported from 1.x would otherwise stop connecting.
 *
 * The prefix becomes [Profile.user] only when that is still blank: 1.x authenticated with
 * the user field, not with the URL, so an explicit user stays the one that connected.
 *
 * @return true when a field changed.
 */
fun Profile.splitLegacyUserInfo(): Boolean {
    val hostParts = host.splitLegacyUserInfo()
    val streamParts = streamHost.splitLegacyUserInfo()
    if (hostParts == null && streamParts == null) {
        return false
    }
    hostParts?.let { host = it.second }
    streamParts?.let { streamHost = it.second }
    if (user.isNullOrEmpty()) {
        user = hostParts?.first ?: streamParts?.first
    }
    return true
}

/**
 * The user and the host after it, or null when [this] is not a plain `user@host`. Credentials
 * in the prefix (`user:pass@host`) are left alone: the 1.x URL that carried them did not
 * connect, so there is nothing to preserve.
 */
private fun String?.splitLegacyUserInfo(): Pair<String?, String>? {
    val value = this ?: return null
    val at = value.indexOf('@')
    if (at < 0 || at == value.lastIndex) {
        return null
    }
    val userInfo = value.substring(0, at)
    if (userInfo.contains(':')) {
        return null
    }
    return userInfo.takeIf { it.isNotEmpty() } to value.substring(at + 1)
}

private const val ILLEGAL_HOST_CHARS = "@/?#\\"
