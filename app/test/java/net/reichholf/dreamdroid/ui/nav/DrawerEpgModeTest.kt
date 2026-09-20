package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawerEpgModeTest {
    @Test
    fun unknownOrBlankDefaultsToList() {
        assertEquals(DrawerEpgMode.LIST, DrawerEpgMode.parse(null))
        assertEquals(DrawerEpgMode.LIST, DrawerEpgMode.parse(""))
        assertEquals(DrawerEpgMode.LIST, DrawerEpgMode.parse("timeline"))
        assertFalse(DrawerEpgMode.isMulti(null))
        assertFalse(DrawerEpgMode.isMulti("list"))
    }

    @Test
    fun multiIsTheOnlyNonListMode() {
        assertEquals(DrawerEpgMode.MULTI, DrawerEpgMode.parse(DrawerEpgMode.MULTI))
        assertTrue(DrawerEpgMode.isMulti(DrawerEpgMode.MULTI))
        assertEquals(DrawerEpgMode.LIST, DrawerEpgMode.parse("LIST"))
    }

    @Test
    fun nestedOnListEpgRequiresMultiOnEpg() {
        assertTrue(
            DrawerEpgMode.isNestedOnListEpg(
                PhoneNavRoutes.MULTI_EPG,
                PhoneNavRoutes.EPG
            )
        )
        assertFalse(
            DrawerEpgMode.isNestedOnListEpg(
                PhoneNavRoutes.MULTI_EPG,
                PhoneNavRoutes.HUB
            )
        )
        assertFalse(
            DrawerEpgMode.isNestedOnListEpg(PhoneNavRoutes.EPG, PhoneNavRoutes.HUB)
        )
        assertFalse(DrawerEpgMode.isNestedOnListEpg(null, null))
    }
}
