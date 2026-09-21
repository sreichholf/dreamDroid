package net.reichholf.dreamdroid.helpers

/** Strip wrapping quotes; treat empty and unknown SSID as missing. */
object WifiSsid {
    private const val UNKNOWN_SSID = "<unknown ssid>"

    fun unquoteWifiSsid(ssid: String?): String? {
        if (ssid.isNullOrEmpty() || ssid == UNKNOWN_SSID) {
            return null
        }
        if (ssid.length >= 2 && ssid.first() == '"' && ssid.last() == '"') {
            val unquoted = ssid.substring(1, ssid.length - 1)
            return unquoted.takeIf { it.isNotEmpty() && it != UNKNOWN_SSID }
        }
        return ssid
    }
}
