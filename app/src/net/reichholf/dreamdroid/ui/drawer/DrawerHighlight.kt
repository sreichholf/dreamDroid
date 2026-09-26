package net.reichholf.dreamdroid.ui.drawer

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.routeKey

/**
 * Map the NavHost destination to the drawer row that should stay selected.
 * Dialog routes return null so an open Power/About sheet does not clear the
 * leaf that launched it. Back uses this same map to retarget the highlight.
 */
object DrawerHighlight {
    fun itemIdForRoute(route: String?, previousRoute: String? = null): Int? {
        val r = routeKey(route) ?: return null
        if (r == PhoneNavRoutes.PICK_SERVICE) {
            return itemIdForRoute(previousRoute)
        }
        return when (r) {
            PhoneNavRoutes.HUB,
            PhoneNavRoutes.CURRENT,
            PhoneNavRoutes.TIMER_EDIT,
            PhoneNavRoutes.TIMER_SERVICE_PICK -> R.id.menu_navigation_services

            PhoneNavRoutes.TOOLS,
            PhoneNavRoutes.DEVICE_INFO,
            PhoneNavRoutes.SIGNAL,
            PhoneNavRoutes.SCREENSHOT -> R.id.menu_navigation_tools

            PhoneNavRoutes.REMOTE -> R.id.menu_navigation_remote

            PhoneNavRoutes.ZAP -> R.id.menu_navigation_zap

            PhoneNavRoutes.EPG,
            PhoneNavRoutes.SERVICE_EPG,
            PhoneNavRoutes.EPG_SEARCH -> R.id.menu_navigation_epg

            PhoneNavRoutes.MULTI_EPG ->
                itemIdForRoute(previousRoute) ?: R.id.menu_navigation_epg

            PhoneNavRoutes.SETTINGS,
            PhoneNavRoutes.BACKUP -> R.id.menu_navigation_settings

            PhoneNavRoutes.PROFILES,
            PhoneNavRoutes.PROFILE_EDIT,
            PhoneNavRoutes.PROFILE_CHECK -> R.id.menu_none

            else -> null
        }
    }
}

interface DrawerRouteHighlighter {
    fun highlightDrawerForRoute(route: String?, previousRoute: String?)
}
