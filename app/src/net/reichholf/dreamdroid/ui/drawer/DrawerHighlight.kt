package net.reichholf.dreamdroid.ui.drawer

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes

/**
 * Map the NavHost destination to the drawer row that should stay selected.
 * Dialog routes return null so an open Power/About sheet does not clear the
 * leaf that launched it. Back uses this same map to retarget the highlight.
 */
object DrawerHighlight {
    fun itemIdForRoute(route: String?, previousRoute: String? = null): Int? {
        val r = route ?: return null
        if (r == PhoneNavRoutes.PICK_SERVICE) {
            return itemIdForRoute(previousRoute)
        }
        return when {
            r == PhoneNavRoutes.HUB ||
                r == PhoneNavRoutes.CURRENT ||
                r == PhoneNavRoutes.TIMER_EDIT ||
                r == PhoneNavRoutes.TIMER_SERVICE_PICK -> R.id.menu_navigation_services
            r == PhoneNavRoutes.TOOLS ||
                r == PhoneNavRoutes.DEVICE_INFO ||
                r == PhoneNavRoutes.SIGNAL ||
                r == PhoneNavRoutes.SCREENSHOT -> R.id.menu_navigation_tools
            r == PhoneNavRoutes.REMOTE -> R.id.menu_navigation_remote
            r == PhoneNavRoutes.ZAP -> R.id.menu_navigation_zap
            r == PhoneNavRoutes.EPG ||
                r.startsWith("service_epg") ||
                r.startsWith("epg_search") -> R.id.menu_navigation_epg
            r == PhoneNavRoutes.MULTI_EPG -> R.id.menu_navigation_multiepg
            r == PhoneNavRoutes.SETTINGS || r == PhoneNavRoutes.BACKUP ->
                R.id.menu_navigation_settings
            r == PhoneNavRoutes.PROFILES ||
                r == PhoneNavRoutes.PROFILE_EDIT ||
                r == PhoneNavRoutes.PROFILE_CHECK -> R.id.menu_none
            else -> null
        }
    }
}

interface DrawerRouteHighlighter {
    fun highlightDrawerForRoute(route: String?, previousRoute: String?)
}
