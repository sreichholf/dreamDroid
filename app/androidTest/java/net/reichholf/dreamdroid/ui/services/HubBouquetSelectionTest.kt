package net.reichholf.dreamdroid.ui.services

import net.reichholf.dreamdroid.enigma.Bouquets
import net.reichholf.dreamdroid.enigma.Service
import org.junit.Assert.assertEquals
import org.junit.Test

class HubBouquetSelectionTest {
    private val favourites =
        Service(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet",
            "Favourites (TV)"
        )
    private val sports =
        Service(
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.sports.tv\" ORDER BY bouquet",
            "Sports"
        )
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
        assertEquals(
            listOf(
                favourites,
                sports,
                Service("ref-p", "Provider"),
                Service("ref-a", "All Services")
            ),
            built
        )
    }

    @Test
    fun httpFailPaintsCachedUserBouquetTabs() {
        val (painted, usedCache) = bouquetsAfterHttpOrCache(
            httpSuccess = false,
            http = Bouquets(),
            cachedTv = items,
            cachedRadio = emptyList()
        )
        assertEquals(true, usedCache)
        assertEquals(items, painted.tv.toList())
        assertEquals(emptyList<Service>(), painted.radio.toList())
    }

    @Test
    fun httpFailWithoutCacheKeepsEmptyHttpBouquets() {
        val http = Bouquets()
        val (painted, usedCache) = bouquetsAfterHttpOrCache(
            httpSuccess = false,
            http = http,
            cachedTv = emptyList(),
            cachedRadio = emptyList()
        )
        assertEquals(false, usedCache)
        assertEquals(http, painted)
    }

    @Test
    fun httpSuccessIgnoresCachedStrip() {
        val http = Bouquets()
        http.tv.add(sports)
        val (painted, usedCache) = bouquetsAfterHttpOrCache(
            httpSuccess = true,
            http = http,
            cachedTv = items,
            cachedRadio = emptyList()
        )
        assertEquals(false, usedCache)
        assertEquals(listOf(sports), painted.tv.toList())
    }
}
