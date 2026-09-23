package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TvMultiEpgSelectionTest {
    private val channels = listOf(
        channel("a", 100L to 200L, 200L to 300L),
        channel("b", 150L to 250L)
    )

    @Test
    fun emptyGridKeepsSelection() {
        assertNull(reconcileTvMultiEpgSelection(emptyList(), "", 0L))
    }

    @Test
    fun unknownChannelFallsBackToFirstBar() {
        assertEquals("a" to 100L, reconcileTvMultiEpgSelection(channels, "", 0L))
    }

    @Test
    fun exactBarIsKept() {
        assertNull(reconcileTvMultiEpgSelection(channels, "a", 200L))
    }

    @Test
    fun missingBarSnapsToOverlapThenFirst() {
        assertEquals("a" to 200L, reconcileTvMultiEpgSelection(channels, "a", 250L))
        assertEquals("b" to 150L, reconcileTvMultiEpgSelection(channels, "b", 900L))
    }

    private fun channel(ref: String, vararg bars: Pair<Long, Long>) = MultiEpgChannel(
        serviceRef = ref,
        serviceName = ref,
        bars = bars.map { (start, end) -> MultiEpgBar(Event(), start, end) }
    )
}
