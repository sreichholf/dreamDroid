package net.reichholf.dreamdroid.multiepg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MultiEpgRestoreTest {
    @Test
    fun bouquetAlwaysFollowsLeafArgsNotSavedComposition() {
        assertEquals("1:7:1:B", MultiEpgRestore.bouquetRef("1:7:1:B"))
        assertEquals("Favourites", MultiEpgRestore.bouquetName("Favourites"))
        assertEquals("", MultiEpgRestore.bouquetRef(null))
        assertEquals("", MultiEpgRestore.bouquetName(null))
    }
}
