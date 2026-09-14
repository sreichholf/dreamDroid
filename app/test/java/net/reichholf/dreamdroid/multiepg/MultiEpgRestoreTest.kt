package net.reichholf.dreamdroid.multiepg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiEpgRestoreTest {
    @Test
    fun bouquetAlwaysFollowsLeafArgsNotSavedComposition() {
        assertEquals("1:7:1:B", MultiEpgRestore.bouquetRef("1:7:1:B"))
        assertEquals("Favourites", MultiEpgRestore.bouquetName("Favourites"))
        assertEquals("", MultiEpgRestore.bouquetRef(null))
        assertEquals("", MultiEpgRestore.bouquetName(null))
    }

    @Test
    fun remountOrBouquetChangeResetsPaintedClock() {
        assertTrue(
            MultiEpgRestore.resetClock(
                remountEpoch = 1,
                bouquetRef = "A",
                savedEpoch = 0,
                savedRef = "A"
            )
        )
        assertTrue(
            MultiEpgRestore.resetClock(
                remountEpoch = 0,
                bouquetRef = "B",
                savedEpoch = 0,
                savedRef = "A"
            )
        )
        assertFalse(
            MultiEpgRestore.resetClock(
                remountEpoch = 2,
                bouquetRef = "A",
                savedEpoch = 2,
                savedRef = "A"
            )
        )
    }
}
