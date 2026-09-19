package net.reichholf.dreamdroid.ui.epg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpgBouquetRestoreTest {
    @Test
    fun pickerResultIsKeptWhenLeafExtrasStillHoldDrawerDefault() {
        val leafRef = "1:7:1:default"
        val pickedRef = "1:7:1:picked"
        assertEquals(pickedRef, EpgBouquetRestore.resolveRef(leafRef, pickedRef))
        assertEquals(
            "Picked TV",
            EpgBouquetRestore.resolveName("Favourites", "Picked TV", pickedRef)
        )
    }

    @Test
    fun blankSessionTakesDrawerLeafExtras() {
        assertEquals("1:7:1:default", EpgBouquetRestore.resolveRef("1:7:1:default", ""))
        assertEquals("Favourites", EpgBouquetRestore.resolveName("Favourites", "", ""))
    }

    @Test
    fun blankLeafLeavesCurrentPick() {
        assertEquals("1:7:1:picked", EpgBouquetRestore.resolveRef("", "1:7:1:picked"))
        assertEquals(
            "Picked TV",
            EpgBouquetRestore.resolveName("", "Picked TV", "1:7:1:picked")
        )
    }
}
