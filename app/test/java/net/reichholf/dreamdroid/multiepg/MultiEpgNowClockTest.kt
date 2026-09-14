package net.reichholf.dreamdroid.multiepg

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiEpgNowClockTest {
    @Test
    fun fakeClockDrivesNowSecWithoutReloading() {
        var wallMs = 1_700_000_000_000L
        assertEquals(1_700_000_000L, MultiEpgNowClock.sec { wallMs })
        wallMs += MultiEpgNowClock.TICK_MS
        assertEquals(1_700_000_060L, MultiEpgNowClock.sec { wallMs })
        wallMs += MultiEpgNowClock.TICK_MS
        assertEquals(1_700_000_120L, MultiEpgNowClock.sec { wallMs })
    }

    @Test
    fun tickIsOneMinute() {
        assertEquals(60_000L, MultiEpgNowClock.TICK_MS)
    }
}
