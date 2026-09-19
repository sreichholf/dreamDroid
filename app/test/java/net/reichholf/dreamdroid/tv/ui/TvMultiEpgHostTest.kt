package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.enigma.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TvMultiEpgHostTest {
    @Test
    fun launchExtrasWinOverDefaultAndFirstBouquet() {
        val first = Service("1:7:1:first", "First")
        assertEquals(
            "1:7:1:extra" to "Favourites",
            resolveTvMultiEpgLaunchBouquet(
                extraRef = " 1:7:1:extra ",
                extraName = " Favourites ",
                defaultRef = "1:7:1:default",
                defaultName = "Default",
                firstBouquet = first
            )
        )
    }

    @Test
    fun blankExtrasFallBackToDefaultThenFirst() {
        val first = Service("1:7:1:first", "First")
        assertEquals(
            "1:7:1:default" to "Default",
            resolveTvMultiEpgLaunchBouquet(
                extraRef = "  ",
                extraName = "Favourites",
                defaultRef = "1:7:1:default",
                defaultName = "Default",
                firstBouquet = first
            )
        )
        assertEquals(
            "1:7:1:first" to "First",
            resolveTvMultiEpgLaunchBouquet(
                extraRef = null,
                extraName = null,
                defaultRef = "",
                defaultName = "Default",
                firstBouquet = first
            )
        )
        assertEquals(
            "1:7:1:extra" to "1:7:1:extra",
            resolveTvMultiEpgLaunchBouquet(
                extraRef = "1:7:1:extra",
                extraName = "  ",
                defaultRef = "1:7:1:default",
                defaultName = "Default",
                firstBouquet = first
            )
        )
    }
}
