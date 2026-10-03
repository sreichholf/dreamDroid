package net.reichholf.dreamdroid.enigma

/**
 * What one receiver's web interface can do. The profile check fills it per profile; until then
 * the defaults hold.
 */
data class WebIfCapabilities(
    /** `/web/epgnownext`; without it the now/next list comes from `/web/epgnow`, now only. */
    val nowNext: Boolean = true,
    /** `/web/sleeptimer`, offered in the drawer. */
    val sleepTimer: Boolean = true,
    /** Requests go out as POST, else as GET. A 405 answer flips it. */
    val postRequest: Boolean = true
)
