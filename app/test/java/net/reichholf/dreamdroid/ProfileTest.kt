package net.reichholf.dreamdroid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileTest {
    @Test
    fun aProfileWithoutTheColumnHidesNothing() {
        val profile = Profile.getDefault()

        assertNull(profile.hiddenBouquetRefs)
        assertEquals(emptySet<String>(), profile.hiddenBouquets())
    }

    @Test
    fun hiddenBouquetsRoundTripSortedAndDropBlanks() {
        val profile = Profile.getDefault()

        profile.setHiddenBouquets(listOf("sports", "", "favourites", "sports"))

        assertEquals(setOf("favourites", "sports"), profile.hiddenBouquets())
        assertEquals("favourites\nsports", profile.hiddenBouquetRefs)
    }

    @Test
    fun showingEverythingClearsTheColumn() {
        val profile = Profile.getDefault()
        profile.setHiddenBouquets(listOf("sports"))

        profile.setHiddenBouquets(emptyList())

        assertNull(profile.hiddenBouquetRefs)
        assertEquals(emptySet<String>(), profile.hiddenBouquets())
    }
}
