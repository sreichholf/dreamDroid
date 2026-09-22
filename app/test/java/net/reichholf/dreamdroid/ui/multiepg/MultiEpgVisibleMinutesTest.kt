package net.reichholf.dreamdroid.ui.multiepg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MultiEpgVisibleMinutesTest {
    @Test
    fun absentKeyRestoresDefaultMinutes() {
        val values = mutableMapOf<String, Any>()
        val access = MapMultiEpgVisibleMinutesAccess(values)
        assertEquals(MULTI_EPG_VISIBLE_MINUTES, readMultiEpgVisibleMinutes(access))
        assertFalse(values.containsKey(MULTI_EPG_VISIBLE_MINUTES_KEY))
        assertEquals("multi_epg_visible_minutes", MULTI_EPG_VISIBLE_MINUTES_KEY)
    }

    @Test
    fun storedMinuteCountRoundTrips() {
        val values = mutableMapOf<String, Any>()
        val access = MapMultiEpgVisibleMinutesAccess(values)
        writeMultiEpgVisibleMinutes(access, 240)
        assertEquals(240, readMultiEpgVisibleMinutes(access))
        assertEquals(240, values[MULTI_EPG_VISIBLE_MINUTES_KEY])
    }
}
