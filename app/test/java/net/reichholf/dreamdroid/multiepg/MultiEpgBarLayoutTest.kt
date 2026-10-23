package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MultiEpgBarLayoutTest {
    @Test
    fun shortEventKeepsMinWidthWhenNeighbourIsFar() {
        val timeline = 1_700_000_000L
        val width = MultiEpgBarLayout.widthDp(
            startSec = timeline,
            endSec = timeline + 30L,
            timelineStartSec = timeline,
            minuteWidthDp = 3f,
            nextStartSec = timeline + 20L * 60L
        )
        assertEquals(MultiEpgBarLayout.MIN_WIDTH_DP, width, 0.01f)
    }

    @Test
    fun minWidthClipsToNextEventStart() {
        val timeline = 1_700_000_000L
        val width = MultiEpgBarLayout.widthDp(
            startSec = timeline,
            endSec = timeline + 30L,
            timelineStartSec = timeline,
            minuteWidthDp = 3f,
            nextStartSec = timeline + 2L * 60L
        )
        // 2 minutes × 3 dp/min = 6 dp, below the 28 dp min width.
        assertEquals(6f, width, 0.01f)
    }

    @Test
    fun naturalWidthUnchangedWhenLongerThanMin() {
        val timeline = 1_700_000_000L
        val width = MultiEpgBarLayout.widthDp(
            startSec = timeline,
            endSec = timeline + 30L * 60L,
            timelineStartSec = timeline,
            minuteWidthDp = 3f
        )
        assertEquals(90f, width, 0.01f)
    }

    @Test
    fun offsetIsMinutesFromTimelineStart() {
        val timeline = 1_700_000_000L
        val x = MultiEpgBarLayout.offsetDp(
            startSec = timeline + 5L * 60L,
            timelineStartSec = timeline,
            minuteWidthDp = 3f
        )
        assertEquals(15f, x, 0.01f)
    }

    @Test
    fun nextStartSecSkipsCurrentAndEarlier() {
        val bars = listOf(
            bar("a", 1000L, 1060L),
            bar("b", 1060L, 2000L),
            bar("c", 2500L, 2600L)
        )
        assertEquals(1060L, MultiEpgBarLayout.nextStartSec(bars, 1000L))
        assertEquals(2500L, MultiEpgBarLayout.nextStartSec(bars, 1060L))
        assertNull(MultiEpgBarLayout.nextStartSec(bars, 2500L))
        assertNull(MultiEpgBarLayout.nextStartSec(emptyList(), 1000L))
    }

    @Test
    fun prevStartSecSkipsCurrentAndLater() {
        val bars = listOf(
            bar("a", 1000L, 1060L),
            bar("b", 1060L, 2000L),
            bar("c", 2500L, 2600L)
        )
        assertNull(MultiEpgBarLayout.prevStartSec(bars, 1000L))
        assertEquals(1000L, MultiEpgBarLayout.prevStartSec(bars, 1060L))
        assertEquals(1060L, MultiEpgBarLayout.prevStartSec(bars, 2500L))
        assertNull(MultiEpgBarLayout.prevStartSec(emptyList(), 1000L))
    }

    @Test
    fun sameStartAsNeighbourYieldsZeroWidth() {
        val timeline = 1_000L
        val width = MultiEpgBarLayout.widthDp(
            startSec = timeline,
            endSec = timeline + 60L,
            timelineStartSec = timeline,
            minuteWidthDp = 3f,
            nextStartSec = timeline
        )
        assertEquals(0f, width, 0.01f)
    }

    @Test
    fun stickyInsetFollowsViewportInsideTheBar() {
        assertEquals(0f, MultiEpgBarLayout.stickyTextInsetDp(100f, 200f, 50f), 0.01f)
        assertEquals(40f, MultiEpgBarLayout.stickyTextInsetDp(100f, 200f, 140f), 0.01f)
        assertEquals(200f, MultiEpgBarLayout.stickyTextInsetDp(100f, 200f, 400f), 0.01f)
    }

    @Test
    fun textWidthIsZeroBelowMinimum() {
        // Bar 100..300 dp, viewport at 260: 40 dp of bar left.
        assertEquals(40f, MultiEpgBarLayout.textWidthDp(100f, 200f, 260f, 32f), 0.01f)
        assertEquals(0f, MultiEpgBarLayout.textWidthDp(100f, 200f, 280f, 32f), 0.01f)
        assertEquals(0f, MultiEpgBarLayout.textWidthDp(0f, 20f, 0f, 32f), 0.01f)
        assertEquals(200f, MultiEpgBarLayout.textWidthDp(100f, 200f, 0f, 32f), 0.01f)
    }

    @Test
    fun gapsCoverHolesBeforeBetweenAndAfterBars() {
        val bars = listOf(
            bar("a", 1600L, 2000L),
            bar("b", 2030L, 3000L),
            bar("c", 3600L, 4000L)
        )
        val gaps = MultiEpgBarLayout.gaps(bars, 1000L, 5000L, minGapSec = 60L)
        assertEquals(listOf(1000L until 1600L, 3000L until 3600L, 4000L until 5000L), gaps)
    }

    @Test
    fun gapsIgnoreBarsOutsideAndOverlappingTheWindow() {
        val bars = listOf(
            bar("a", 0L, 1200L),
            bar("b", 1200L, 1500L),
            bar("c", 6000L, 7000L)
        )
        assertEquals(
            listOf(1500L until 5000L),
            MultiEpgBarLayout.gaps(bars, 1000L, 5000L, minGapSec = 60L)
        )
        assertEquals(
            listOf(1000L until 5000L),
            MultiEpgBarLayout.gaps(emptyList(), 1000L, 5000L, minGapSec = 60L)
        )
        assertEquals(
            emptyList<LongRange>(),
            MultiEpgBarLayout.gaps(listOf(bar("d", 900L, 5100L)), 1000L, 5000L, 60L)
        )
    }

    private fun bar(id: String, startSec: Long, endSec: Long): MultiEpgBar = MultiEpgBar(
        event = Event(
            eventId = id,
            title = id,
            start = startSec.toString(),
            duration = (endSec - startSec).toString()
        ),
        startSec = startSec,
        endSec = endSec
    )
}
