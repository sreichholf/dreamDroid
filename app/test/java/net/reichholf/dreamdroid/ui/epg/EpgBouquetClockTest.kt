package net.reichholf.dreamdroid.ui.epg

import org.junit.Assert.assertEquals
import org.junit.Test

class EpgBouquetClockTest {
    @Test
    fun pickedTimeIsNotAdvancedToWallClock() {
        assertEquals(1_700_000_000, EpgBouquetClock.keepPicked(1_700_000_000, 1_700_000_500))
        assertEquals(50, EpgBouquetClock.keepPicked(50, 100))
    }
}
