package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.enigma.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TvMultiEpgHostTest {
    @Test
    fun theHubsBouquetWins() {
        val first = Service("1:7:1:first", "First")
        assertEquals(
            "1:7:1:extra" to "Favourites",
            resolveTvMultiEpgLaunchBouquet(
                extraRef = " 1:7:1:extra ",
                extraName = " Favourites ",
                firstBouquet = first
            )
        )
    }

    @Test
    fun aBlankRefFallsBackToTheFirstBouquetOnly() {
        val first = Service("1:7:1:first", "First")
        assertEquals(
            "1:7:1:first" to "First",
            resolveTvMultiEpgLaunchBouquet(
                extraRef = "  ",
                extraName = "Favourites",
                firstBouquet = first
            )
        )
        assertEquals(
            "1:7:1:first" to "First",
            resolveTvMultiEpgLaunchBouquet(
                extraRef = null,
                extraName = null,
                firstBouquet = first
            )
        )
        assertEquals(
            "1:7:1:extra" to "1:7:1:extra",
            resolveTvMultiEpgLaunchBouquet(
                extraRef = "1:7:1:extra",
                extraName = "  ",
                firstBouquet = first
            )
        )
    }
}
