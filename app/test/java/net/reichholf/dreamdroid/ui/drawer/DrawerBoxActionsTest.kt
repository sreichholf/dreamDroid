package net.reichholf.dreamdroid.ui.drawer

import net.reichholf.dreamdroid.R
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawerBoxActionsTest {
    @Test
    fun sleepTimerHiddenWhenFeatureOff() {
        val shown = drawerBoxActions(sleepTimerAvailable = false).map { it.id }
        assertFalse(shown.contains(R.id.menu_navigation_sleeptimer))
        assertTrue(shown.contains(R.id.menu_navigation_power))
    }

    @Test
    fun sleepTimerShownWhenFeatureOn() {
        val shown = drawerBoxActions(sleepTimerAvailable = true).map { it.id }
        assertTrue(shown.contains(R.id.menu_navigation_sleeptimer))
    }
}
