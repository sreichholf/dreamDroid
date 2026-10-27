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

    @Test
    fun slidingChunksKeepsOnlyTheVisibleChunk() {
        val chunk = MultiEpgWindows.chunkContaining(MultiEpgWindows.CHUNK_SECONDS + 10L)
        val now = chunk.startSec + 3_600L
        val want = MultiEpgWindows.slidingChunks(
            originFloorSec = now,
            visibleStartSec = now,
            visibleEndSec = now + 7_200L
        )
        assertEquals(listOf(chunk.startSec), want)
    }
}
