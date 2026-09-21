package net.reichholf.dreamdroid.ui.video

import net.reichholf.dreamdroid.enigma.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverlayBouquetBarTest {
    @Test
    fun tvBouquetsPrecedeRadioAndRootsDrop() {
        val excluded = setOf("1:7:1:0:0:0:0:0:0:0:")
        val tv = listOf(
            Service("1:7:1:0:0:0:0:0:0:0:", "TV"),
            Service("fav", "Favourites"),
            Service("1:7:1:0:0:0:0:0:0:0:FROM PROVIDERS", "Providers")
        )
        val radio = listOf(
            Service("radio", "Radio"),
            Service("fav", "Favourites")
        )
        val bar = overlayBouquets(tv, radio, excluded)
        assertEquals(listOf("Favourites", "Radio"), bar.map { it.name })
    }

    @Test
    fun emptyInputsStayEmpty() {
        assertEquals(emptyList<Service>(), overlayBouquets(emptyList(), emptyList(), emptySet()))
    }
}
