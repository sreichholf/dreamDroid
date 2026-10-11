package net.reichholf.dreamdroid.multiepg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiEpgWindowsTtlTest {
    private val day = MultiEpgWindows.CHUNK_SECONDS
    private val now = 1_000L * day + 3_600L
    private val today = MultiEpgWindows.chunkContaining(now).startSec

    @Test
    fun todayAndTomorrowUseTheShortTtl() {
        assertEquals(MultiEpgWindows.DEFAULT_TTL_MS, MultiEpgWindows.ttlMs(today, now))
        assertEquals(MultiEpgWindows.DEFAULT_TTL_MS, MultiEpgWindows.ttlMs(today + day, now))
    }

    @Test
    fun laterDaysUseTheLongTtl() {
        assertEquals(MultiEpgWindows.FAR_TTL_MS, MultiEpgWindows.ttlMs(today + 2 * day, now))
        assertEquals(MultiEpgWindows.FAR_TTL_MS, MultiEpgWindows.ttlMs(today + 6 * day, now))
    }
}
