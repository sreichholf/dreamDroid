package net.reichholf.dreamdroid.multiepg

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiEpgZoomTest {
    @Test
    fun optionsMatchGraphMultiEpgSpans() {
        assertArrayEquals(intArrayOf(60, 120, 240, 300), MultiEpgZoom.OPTIONS_MINUTES)
        assertEquals(120, MultiEpgZoom.DEFAULT_MINUTES)
        assertEquals(2, MultiEpgZoom.hours(MultiEpgZoom.DEFAULT_MINUTES))
    }

    @Test
    fun minuteWidthScalesInverselyWithSpan() {
        assertEquals(3f, MultiEpgZoom.minuteWidthDp(120), 0.01f)
        assertEquals(6f, MultiEpgZoom.minuteWidthDp(60), 0.01f)
        assertEquals(1.5f, MultiEpgZoom.minuteWidthDp(240), 0.01f)
        assertEquals(1.2f, MultiEpgZoom.minuteWidthDp(300), 0.01f)
    }

    @Test
    fun coerceSnapsToNearestOption() {
        assertEquals(120, MultiEpgZoom.coerce(100))
        assertEquals(60, MultiEpgZoom.coerce(70))
        assertEquals(300, MultiEpgZoom.coerce(1000))
    }

    @Test
    fun oneHourUsesHalfHourTicks() {
        assertEquals(1800L, MultiEpgZoom.tickStepSec(60))
        assertEquals(3600L, MultiEpgZoom.tickStepSec(120))
        assertEquals(3600L, MultiEpgZoom.tickStepSec(300))
    }
}
