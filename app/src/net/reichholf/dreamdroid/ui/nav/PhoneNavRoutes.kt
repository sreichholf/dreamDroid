package net.reichholf.dreamdroid.ui.nav

/**
 * Compose Navigation route ids for the phone shell NavHost.
 * Expand as more drawer destinations migrate off [net.reichholf.dreamdroid.fragment.helper.NavigationHelper].
 */
object PhoneNavRoutes {
    const val DEVICE_INFO = "device_info"
    const val SIGNAL = "signal"
    const val SCREENSHOT = "screenshot"
    const val CURRENT = "current"
    const val ZAP = "zap"
    const val BACKUP = "backup"
    const val PROFILES = "profiles"
    const val EPG = "epg"
    const val REMOTE = "remote"
    const val HUB = "hub"

    /** Nested service EPG (typed string args). */
    const val SERVICE_EPG = "service_epg/{serviceRef}?serviceName={serviceName}"

    const val ARG_SERVICE_REF = "serviceRef"
    const val ARG_SERVICE_NAME = "serviceName"
}
