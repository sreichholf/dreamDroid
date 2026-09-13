package net.reichholf.dreamdroid.multiepg

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiEpgTextSizeTest {
    @Test
    fun defaultIsComfortable() {
        assertEquals(MultiEpgTextSize.Comfortable, MultiEpgTextSize.DEFAULT)
        assertEquals("comfortable", MultiEpgTextSize.DEFAULT.prefValue)
    }

    @Test
    fun fromPrefFallsBackToComfortable() {
        assertEquals(MultiEpgTextSize.Comfortable, MultiEpgTextSize.fromPref(null))
        assertEquals(MultiEpgTextSize.Comfortable, MultiEpgTextSize.fromPref(""))
        assertEquals(MultiEpgTextSize.Comfortable, MultiEpgTextSize.fromPref("huge"))
        assertEquals(MultiEpgTextSize.Compact, MultiEpgTextSize.fromPref("compact"))
        assertEquals(MultiEpgTextSize.Comfortable, MultiEpgTextSize.fromPref("comfortable"))
    }

    @Test
    fun comfortableRowsAndClocksAreLarger() {
        assertEquals(36f, MultiEpgTextSize.Compact.rowHeightDp(1f), 0.01f)
        assertEquals(48f, MultiEpgTextSize.Comfortable.rowHeightDp(1f), 0.01f)
        assertEquals(12f, MultiEpgTextSize.Compact.clockSizeDp(1f), 0.01f)
        assertEquals(16f, MultiEpgTextSize.Comfortable.clockSizeDp(1f), 0.01f)
        assertEquals(100f, MultiEpgTextSize.Compact.channelWidthDp, 0.01f)
        assertEquals(112f, MultiEpgTextSize.Comfortable.channelWidthDp, 0.01f)
    }

    @Test
    fun fontScaleGrowsRowsAndClocksButNotBelowDesign() {
        assertEquals(72f, MultiEpgTextSize.Compact.rowHeightDp(2f), 0.01f)
        assertEquals(24f, MultiEpgTextSize.Compact.clockSizeDp(2f), 0.01f)
        assertEquals(48f, MultiEpgTextSize.Comfortable.rowHeightDp(0.8f), 0.01f)
        assertEquals(16f, MultiEpgTextSize.Comfortable.clockSizeDp(0.8f), 0.01f)
    }
}
