package net.reichholf.dreamdroid.ui.drawer

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DrawerHighlightTest {
    @Test
    fun backFromNestedToolsLeafHighlightsTools() {
        assertEquals(
            R.id.menu_navigation_tools,
            DrawerHighlight.itemIdForRoute(PhoneNavRoutes.SIGNAL)
        )
        assertEquals(
            R.id.menu_navigation_tools,
            DrawerHighlight.itemIdForRoute(PhoneNavRoutes.TOOLS)
        )
    }

    @Test
    fun backFromZapHighlightsZap() {
        assertEquals(
            R.id.menu_navigation_zap,
            DrawerHighlight.itemIdForRoute(PhoneNavRoutes.ZAP)
        )
    }

    @Test
    fun pickServiceKeepsPreviousLeaf() {
        assertEquals(
            R.id.menu_navigation_zap,
            DrawerHighlight.itemIdForRoute(
                PhoneNavRoutes.PICK_SERVICE,
                previousRoute = PhoneNavRoutes.ZAP
            )
        )
        assertEquals(
            R.id.menu_navigation_epg,
            DrawerHighlight.itemIdForRoute(
                PhoneNavRoutes.PICK_SERVICE,
                previousRoute = PhoneNavRoutes.EPG
            )
        )
    }

    @Test
    fun dialogsDoNotStealTheHighlight() {
        assertNull(DrawerHighlight.itemIdForRoute(PhoneNavRoutes.POWER))
        assertNull(DrawerHighlight.itemIdForRoute(PhoneNavRoutes.ABOUT))
    }

    @Test
    fun profilesClearsTheHighlight() {
        assertEquals(
            R.id.menu_none,
            DrawerHighlight.itemIdForRoute(PhoneNavRoutes.PROFILES)
        )
    }

    @Test
    fun epgSearchQueryParamKeepsEpgHighlight() {
        assertEquals(
            R.id.menu_navigation_epg,
            DrawerHighlight.itemIdForRoute(PhoneNavRoutes.EPG_SEARCH)
        )
        assertEquals(
            R.id.menu_navigation_epg,
            DrawerHighlight.itemIdForRoute(
                "epg_search?query=Tagesschau%20%2F%20Wetter"
            )
        )
    }

    @Test
    fun argumentedRoutesKeepTheirDrawerRow() {
        assertEquals(
            R.id.menu_none,
            DrawerHighlight.itemIdForRoute("profile_edit?profileId=3&name=Box")
        )
        assertEquals(
            R.id.menu_navigation_services,
            DrawerHighlight.itemIdForRoute("timer_edit?create=true&begin=10")
        )
        assertEquals(
            R.id.menu_navigation_epg,
            DrawerHighlight.itemIdForRoute("multi_epg?serviceRef=1%3A0%3A1")
        )
    }

    @Test
    fun nestedMultiEpgHighlightsPreviousLeaf() {
        assertEquals(
            R.id.menu_navigation_services,
            DrawerHighlight.itemIdForRoute(
                PhoneNavRoutes.MULTI_EPG,
                previousRoute = PhoneNavRoutes.HUB
            )
        )
        assertEquals(
            R.id.menu_navigation_epg,
            DrawerHighlight.itemIdForRoute(
                PhoneNavRoutes.MULTI_EPG,
                previousRoute = PhoneNavRoutes.EPG
            )
        )
        assertEquals(
            R.id.menu_navigation_epg,
            DrawerHighlight.itemIdForRoute(PhoneNavRoutes.MULTI_EPG)
        )
        assertEquals(
            R.id.menu_navigation_epg,
            DrawerHighlight.itemIdForRoute("service_epg/1:0:1")
        )
    }

    @Test
    fun drawerOpenedMultiEpgKeepsEpgHighlight() {
        assertEquals(
            R.id.menu_navigation_epg,
            DrawerHighlight.itemIdForRoute(
                PhoneNavRoutes.MULTI_EPG,
                previousRoute = PhoneNavRoutes.EPG
            )
        )
    }
}
