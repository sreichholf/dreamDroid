package net.reichholf.dreamdroid.ui.nav

import net.reichholf.dreamdroid.R
import org.junit.Assert.assertEquals
import org.junit.Test

class StartScreenTest {
    @Test
    fun mapsMainDrawerValuesToMenuIds() {
        assertEquals(R.id.menu_navigation_services, StartScreen.menuId(StartScreen.VALUE_SERVICES))
        assertEquals(R.id.menu_navigation_epg, StartScreen.menuId(StartScreen.VALUE_EPG))
        assertEquals(R.id.menu_navigation_remote, StartScreen.menuId(StartScreen.VALUE_REMOTE))
        assertEquals(R.id.menu_navigation_current, StartScreen.menuId(StartScreen.VALUE_CURRENT))
        assertEquals(R.id.menu_navigation_zap, StartScreen.menuId(StartScreen.VALUE_ZAP))
        assertEquals(R.id.menu_navigation_tools, StartScreen.menuId(StartScreen.VALUE_TOOLS))
        assertEquals(R.id.menu_navigation_settings, StartScreen.menuId(StartScreen.VALUE_SETTINGS))
    }

    @Test
    fun unknownValueDefaultsToServices() {
        assertEquals(R.id.menu_navigation_services, StartScreen.menuId("nope"))
    }
}
