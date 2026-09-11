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
    const val SETTINGS = "settings"
    const val HUB = "hub"

    /** Phase 2.1g-ii-b: About as a Navigation Compose `dialog` destination. */
    const val ABOUT = "about"

    /** Nested service EPG (typed string args). */
    const val SERVICE_EPG = "service_epg/{serviceRef}?serviceName={serviceName}"

    /** Nested EPG search (typed query string). */
    const val EPG_SEARCH = "epg_search/{query}"

    const val ARG_SERVICE_REF = "serviceRef"
    const val ARG_SERVICE_NAME = "serviceName"
    const val ARG_QUERY = "query"

    /** Nested bouquet/service picker (Zap / EPG bouquet). */
    const val PICK_SERVICE = "pick_service"

    /** Nested profile create/edit (args via host). */
    const val PROFILE_EDIT = "profile_edit"

    /** Nested timer create/edit (args via host). */
    const val TIMER_EDIT = "timer_edit"

    /** Nested timer service pick (from timer edit). */
    const val TIMER_SERVICE_PICK = "timer_service_pick"
}
