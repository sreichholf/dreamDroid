package net.reichholf.dreamdroid.ui.multiepg

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MultiEpgVisibleMinutesTest {
    @Test
    fun absentKeyRestoresDefaultMinutes() {
        val handle = SavedStateHandle()
        assertEquals(MULTI_EPG_VISIBLE_MINUTES, readMultiEpgVisibleMinutes(handle))
        assertFalse(handle.contains(MULTI_EPG_VISIBLE_MINUTES_KEY))
        assertEquals("multi_epg_visible_minutes", MULTI_EPG_VISIBLE_MINUTES_KEY)
    }

    @Test
    fun storedMinuteCountRoundTrips() {
        val handle = SavedStateHandle()
        writeMultiEpgVisibleMinutes(handle, 240)
        assertEquals(240, readMultiEpgVisibleMinutes(handle))
        assertEquals(240, handle.get<Any>(MULTI_EPG_VISIBLE_MINUTES_KEY))
    }
}
