package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.enigma.Timer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvTimerHostTest {
    @Test
    fun toggleFlipsDisabledFlag() {
        assertEquals("0", tvTimerToggledDisabled("1"))
        assertEquals("1", tvTimerToggledDisabled("0"))
        assertEquals("1", tvTimerToggledDisabled(""))
    }

    @Test
    fun loadSuccessUsesLiveAndIgnoresSnapshot() {
        val live = listOf(Timer(name = "Live"))
        val snap = listOf(Timer(name = "Snap"))
        val paint = tvTimerPaintFromLoad(true, live, snap, "err")
        assertEquals(live, paint.timers)
        assertTrue(paint.success)
        assertNull(paint.errorText)
    }

    @Test
    fun loadFailurePaintsSnapshotWhenPresent() {
        val snap = listOf(Timer(name = "Snap"))
        val paint = tvTimerPaintFromLoad(false, emptyList(), snap, "err")
        assertEquals(snap, paint.timers)
        assertTrue(paint.success)
        assertNull(paint.errorText)
    }

    @Test
    fun loadFailureWithoutSnapshotKeepsError() {
        val paint = tvTimerPaintFromLoad(false, emptyList(), null, "boom")
        assertTrue(paint.timers.isEmpty())
        assertFalse(paint.success)
        assertEquals("boom", paint.errorText)
    }

    @Test
    fun emptySnapshotOnFailureIsSuccessEmptyList() {
        val paint = tvTimerPaintFromLoad(false, emptyList(), emptyList(), "err")
        assertTrue(paint.success)
        assertTrue(paint.timers.isEmpty())
        assertNull(paint.errorText)
    }

    @Test
    fun repeatedBitsMatchPhoneValues() {
        val days = booleanArrayOf(true, false, true, false, false, false, false)
        assertEquals(5, tvTimerRepeatedValue(days))
        val parsed = tvTimerCheckedDays(5)
        assertTrue(parsed[0])
        assertFalse(parsed[1])
        assertTrue(parsed[2])
        assertEquals(127, tvTimerRepeatedValue(BooleanArray(7) { true }))
        assertEquals(
            31,
            tvTimerRepeatedValue(
                booleanArrayOf(true, true, true, true, true, false, false)
            )
        )
    }
}
