package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
