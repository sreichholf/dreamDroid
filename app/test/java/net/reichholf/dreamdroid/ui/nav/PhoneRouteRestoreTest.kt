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

    @Test
    fun timerTagsKeepTheirShape() {
        assertEquals("timer_edit:new:100", TimerEdit(create = true, begin = "100").tag())
        assertEquals(
            "timer_edit:1:0:1:100",
            TimerEdit(create = false, reference = "1:0:1", begin = "100").tag()
        )
    }
}
