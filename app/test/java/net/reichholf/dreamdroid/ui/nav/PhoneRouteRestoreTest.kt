package net.reichholf.dreamdroid.ui.nav

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhoneRouteRestoreTest {
    @Test
    fun savedProfileEditIdFallsBackToTheStartRoute() {
        assertEquals(Hub, startDestinationForSavedId("profile_edit", Hub))
    }

    @Test
    fun savedEpgIdStartsEpg() {
        assertEquals(Epg(), startDestinationForSavedId(PhoneNavRoutes.EPG, Hub))
    }

    @Test
    fun savedServiceEpgIdFallsBackToTheStartRoute() {
        assertEquals(Hub, startDestinationForSavedId("service_epg/1:0:1:ref", Hub))
    }

    @Test
    fun unknownIdFallsBackToTheStartRoute() {
        assertEquals(DeviceInfo, startDestinationForSavedId("not_a_route", DeviceInfo))
    }
}
