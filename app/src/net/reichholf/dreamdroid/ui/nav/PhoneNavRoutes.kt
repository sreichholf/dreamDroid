package net.reichholf.dreamdroid.ui.nav

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Compose Navigation route ids for the phone shell NavHost.
 * Drawer menu ids map here via [NavigationHelper].
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
    const val MULTI_EPG = "multi_epg"
    const val REMOTE = "remote"
    const val SETTINGS = "settings"
    const val HUB = "hub"
    const val TOOLS = "tools"

    /** Phase 2.1g-ii-b: About as a Navigation Compose `dialog` destination. */
    const val ABOUT = "about"

    /** Phase 2.1g-ii-c: remaining drawer modals as Navigation Compose `dialog`s. */
    const val POWER = "power"
    const val SEND_MESSAGE = "send_message"
    const val SLEEP_TIMER = "sleep_timer"
    const val CHANGELOG = "changelog"

    /** Full-screen gate while the active profile is checked / after hard failure. */
    const val PROFILE_CHECK = "profile_check"

    /** Nested service EPG (typed string args). */
    const val SERVICE_EPG = "service_epg/{serviceRef}?serviceName={serviceName}"

    /**
     * Nested EPG search. The term is a query parameter so titles that contain `/`
     * cannot add extra path segments (Navigation splits `{query}` path args).
     */
    const val EPG_SEARCH = "epg_search?query={query}"

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

    /**
     * TV & Movies / Tools paint chrome on `shell_destination_nav`. Other routes must
     * hide that overlay from the destination change, not Hub dispose — otherwise the
     * bar is still visible on the first frame of timer/profile edit.
     */
    fun showsShellDestinationBar(route: String?): Boolean = route == HUB || route == TOOLS

    private const val ENCODING = "UTF-8"

    /** Filled [EPG_SEARCH] route; percent-encodes [query] as a query parameter. */
    fun epgSearchRoute(query: String): String = "epg_search?$ARG_QUERY=${encodeQueryParam(query)}"

    /**
     * Inverse of [epgSearchRoute]. Matches the percent-decode Navigation applies
     * when reading [ARG_QUERY] from the filled URI.
     */
    fun queryFromEpgSearchRoute(route: String): String {
        val prefix = "?$ARG_QUERY="
        val start = route.indexOf(prefix)
        if (start < 0) {
            return ""
        }
        val encoded = route.substring(start + prefix.length).substringBefore('&')
        return decodeQueryParam(encoded)
    }

    private fun encodeQueryParam(value: String): String =
        URLEncoder.encode(value, ENCODING).replace("+", "%20")

    private fun decodeQueryParam(value: String): String = URLDecoder.decode(value, ENCODING)
}
