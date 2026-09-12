package net.reichholf.dreamdroid.ui.services

import net.reichholf.dreamdroid.enigma.Service
import org.junit.Assert.assertEquals
import org.junit.Test

class HubBouquetSelectionTest {
    private val favourites = Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet", "Favourites (TV)")
    private val sports = Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet", "Sports")
    private val items = listOf(favourites, sports)

    @Test
    fun prefersSessionCurrentOverDefault() {
        val (idx, ref) = resolveBouquetSelection(items, sports.reference, favourites.reference)
        assertEquals(1, idx)
        assertEquals(sports.reference, ref)
    }

    @Test
    fun usesDefaultBouquetWhenNoSessionCurrent() {
        val (idx, ref) = resolveBouquetSelection(items, null, sports.reference)
        assertEquals(1, idx)
        assertEquals(sports.reference, ref)
    }

    @Test
    fun fallsBackToFavouritesSectionWhenNoDefault() {
        val (idx, ref) = resolveBouquetSelection(items, null, null)
        assertEquals(0, idx)
        assertEquals(favourites.reference, ref)
    }

    @Test
    fun unknownDefaultFallsBackToFirst() {
        val (idx, ref) = resolveBouquetSelection(items, null, "missing")
        assertEquals(0, idx)
        assertEquals(favourites.reference, ref)
    }

    @Test
    fun buildDedicatedSkipsBouquetsLabelWhenLoaded() {
        val labels = arrayOf("Bouquets", "Provider", "All Services")
        val refs = arrayOf("ref-b", "ref-p", "ref-a")
        val built = buildDedicatedBouquets(items, labels, refs)
        assertEquals(listOf(favourites, sports, Service("ref-p", "Provider"), Service("ref-a", "All Services")), built)
    }
}
