package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgChannelKey
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
        assertNull(reconcileTvMultiEpgSelection(emptyList(), null, 0L))
    }

    @Test
    fun noSelectionFallsBackToFirstBar() {
        assertEquals(channels[0].key to 100L, reconcileTvMultiEpgSelection(channels, null, 0L))
    }

    @Test
    fun exactBarIsKept() {
        assertNull(reconcileTvMultiEpgSelection(channels, channels[0].key, 200L))
    }

    @Test
    fun missingBarSnapsToOverlapThenFirst() {
        assertEquals(
            channels[0].key to 200L,
            reconcileTvMultiEpgSelection(channels, channels[0].key, 250L)
        )
        assertEquals(
            channels[1].key to 150L,
            reconcileTvMultiEpgSelection(channels, channels[1].key, 900L)
        )
    }

    @Test
    fun repeatedServiceKeepsTheSecondOccurrence() {
        val repeated = listOf(
            channel("a", 100L to 200L),
            channel("a", 100L to 200L, occurrence = 1)
        )
        assertNull(
            reconcileTvMultiEpgSelection(repeated, MultiEpgChannelKey("a", 1), 100L)
        )
    }

    @Test
    fun vanishedOccurrenceFallsBackToTheRemainingRow() {
        val remaining = channel("a", 100L to 200L)

        assertEquals(
            remaining.key to 100L,
            reconcileTvMultiEpgSelection(
                listOf(remaining),
                MultiEpgChannelKey("a", 1),
                0L
            )
        )
    }

    @Test
    fun vanishedOccurrenceKeepsTheBarOfTheRemainingRow() {
        val remaining = channel("a", 100L to 200L, 200L to 300L)

        assertEquals(
            remaining.key to 200L,
            reconcileTvMultiEpgSelection(
                listOf(remaining),
                MultiEpgChannelKey("a", 1),
                200L
            )
        )
    }

    private fun channel(ref: String, vararg bars: Pair<Long, Long>, occurrence: Int = 0) =
        MultiEpgChannel(
            occurrence = occurrence,
            serviceRef = ref,
            serviceName = ref,
            bars = bars.map { (start, end) -> MultiEpgBar(Event(), start, end) }
        )
}
