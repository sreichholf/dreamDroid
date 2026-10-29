package net.reichholf.dreamdroid.multiepg

import java.util.Locale
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiEpgTimeLabelsTest {
    private val utc = TimeZone.getTimeZone("UTC")

    /** 2023-11-14T22:13:20Z, a Tuesday. */
    private val now = 1_700_000_000L

    /** 2023-11-15T00:00:00Z. */
    private val midnight = 1_700_006_400L

    @Test
    fun fullLabelPrefixesTodayOnTheSameLocalDay() {
        val label = MultiEpgTimeLabels.formatVisibleDay(
            visibleSec = now + 3600L,
            nowSec = now,
            todayLabel = "Today",
            locale = Locale.US,
            timeZone = utc
        )
        assertTrue(label.startsWith("Today · "), label)
        assertTrue(label.contains("2023"), label)
    }

    @Test
    fun fullLabelForAnotherDayUsesTheLongDate() {
        val label = MultiEpgTimeLabels.formatVisibleDay(
            visibleSec = now + 86_400L,
            nowSec = now,
            todayLabel = "Today",
            locale = Locale.US,
            timeZone = utc
        )
        assertFalse(label.startsWith("Today"), label)
        assertTrue(label.contains("2023"), label)
        assertTrue(label.contains("November"), label)
    }

    @Test
    fun localDayKeyHoldsWithinADayAndChangesAtLocalMidnight() {
        assertEquals(
            MultiEpgTimeLabels.localDayKey(midnight, utc),
            MultiEpgTimeLabels.localDayKey(midnight + 23L * 3600L, utc)
        )
        assertNotEquals(
            MultiEpgTimeLabels.localDayKey(midnight - 1L, utc),
            MultiEpgTimeLabels.localDayKey(midnight, utc)
        )
    }

    @Test
    fun sameLocalDayFollowsTheGivenZone() {
        val earlier = midnight - 3600L
        assertFalse(MultiEpgTimeLabels.sameLocalDay(midnight, earlier, utc))
        assertTrue(
            MultiEpgTimeLabels.sameLocalDay(
                midnight,
                earlier,
                TimeZone.getTimeZone("GMT+13:00")
            )
        )
    }
}
