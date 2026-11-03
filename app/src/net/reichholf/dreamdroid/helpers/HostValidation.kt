package net.reichholf.dreamdroid.helpers

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

private const val ILLEGAL_HOST_CHARS = "@/?#\\"
