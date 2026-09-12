package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class MultiEpgWindowsTest {
    @Test
    fun slidingChunksStartsAtNowNotUtcMidnight() {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val lateNow = chunk.endSec - 600L
        val want = MultiEpgWindows.slidingChunks(
            originFloorSec = lateNow,
            visibleStartSec = lateNow,
            visibleEndSec = lateNow + 7200L,
        )
        assertEquals(chunk.startSec, want.first())
        assertFalse(want.contains(chunk.startSec - MultiEpgWindows.CHUNK_SECONDS))
        assertTrue(want.contains(chunk.startSec + MultiEpgWindows.CHUNK_SECONDS))
    }

    @Test
    fun slidingChunksDropsTodayOnceViewportIsTwoDaysAhead() {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val now = chunk.endSec - 600L
        val day2 = chunk.startSec + 2L * MultiEpgWindows.CHUNK_SECONDS
        val want = MultiEpgWindows.slidingChunks(
            originFloorSec = now,
            visibleStartSec = day2 + 3600L,
            visibleEndSec = day2 + 3600L + 7200L,
        )
        assertFalse(want.contains(chunk.startSec))
        assertTrue(want.contains(day2))
    }

    @Test
    fun formatVisibleDayPrefixesTodayOnSameLocalDay() {
        val tz = TimeZone.getTimeZone("UTC")
        val now = 1_700_000_000L
        val label = MultiEpgTimeLabels.formatVisibleDay(
            visibleSec = now + 3600L,
            nowSec = now,
            todayLabel = "Today",
            locale = Locale.US,
            timeZone = tz,
        )
        assertTrue(label.startsWith("Today · "))
        val other = MultiEpgTimeLabels.formatVisibleDay(
            visibleSec = now + MultiEpgWindows.CHUNK_SECONDS,
            nowSec = now,
            todayLabel = "Today",
            locale = Locale.US,
            timeZone = tz,
        )
        assertFalse(other.startsWith("Today"))
    }

    @Test
    fun paintedTimelineStartIsEarliestAiringProgramme() {
        val now = 10_000L
        val events = listOf(
            Event(eventId = "a", title = "Early", start = "7000", duration = "5000"),
            Event(eventId = "b", title = "Later", start = "9000", duration = "3000"),
            Event(eventId = "c", title = "Finished", start = "1000", duration = "1000"),
        )
        assertEquals(
            7000L,
            MultiEpgWindows.paintedTimelineStart(
                nowSec = now,
                minWindowStartSec = 0L,
                events = events,
            ),
        )
    }

    @Test
    fun paintedTimelineStartFallsBackToNowWhenNothingAiring() {
        val now = 10_000L
        val events = listOf(
            Event(eventId = "c", title = "Finished", start = "1000", duration = "1000"),
            Event(eventId = "d", title = "Upcoming", start = "20000", duration = "1000"),
        )
        assertEquals(
            now,
            MultiEpgWindows.paintedTimelineStart(
                nowSec = now,
                minWindowStartSec = 0L,
                events = events,
            ),
        )
    }

    @Test
    fun paintedTimelineStartUsesOldestWindowOnceNowIsDropped() {
        val now = 10_000L
        assertEquals(
            50_000L,
            MultiEpgWindows.paintedTimelineStart(
                nowSec = now,
                minWindowStartSec = 50_000L,
                events = listOf(
                    Event(eventId = "a", title = "Early", start = "7000", duration = "5000"),
                ),
            ),
        )
    }
}
