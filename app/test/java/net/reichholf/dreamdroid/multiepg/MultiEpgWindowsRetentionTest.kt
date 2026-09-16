package net.reichholf.dreamdroid.multiepg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiEpgWindowsRetentionTest {
    @Test
    fun retentionIsTwoDays() {
        assertEquals(2L * MultiEpgWindows.CHUNK_SECONDS, MultiEpgWindows.RETENTION_SECONDS)
    }

    @Test
    fun retentionCutoffIsNowMinusTwoDays() {
        val now = 10_000_000L
        assertEquals(
            now - MultiEpgWindows.RETENTION_SECONDS,
            MultiEpgWindows.retentionCutoffSec(now)
        )
    }
}
