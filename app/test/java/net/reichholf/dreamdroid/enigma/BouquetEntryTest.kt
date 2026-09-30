package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.enigma.BouquetEntryKind.Alternative
import net.reichholf.dreamdroid.enigma.BouquetEntryKind.Bouquet
import net.reichholf.dreamdroid.enigma.BouquetEntryKind.Directory
import net.reichholf.dreamdroid.enigma.BouquetEntryKind.Marker
import net.reichholf.dreamdroid.enigma.BouquetEntryKind.Service
import net.reichholf.dreamdroid.enigma.BouquetEntryKind.Stream
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BouquetEntryTest {
    @Test
    fun bouquetIndexRowsAreBouquets() {
        assertEquals(listOf(Bouquet), kinds("getservices_roots_tv.xml", atRoot = true))
        assertEquals(listOf(Bouquet, Bouquet), kinds("getservices_roots_radio.xml", atRoot = true))
    }

    @Test
    fun bouquetEntriesAreServicesMarkersAndAlternatives() {
        assertEquals(
            listOf(Service, Marker, Service, Alternative),
            kinds("getservices_with_alternative.xml", atRoot = false)
        )
    }

    @Test
    fun satelliteFoldersAreDirectoriesNotBouquets() {
        val kinds = kinds("satelliteslist_tv.xml", atRoot = false)

        assertTrue(kinds.isNotEmpty())
        assertTrue(kinds.all { it == Directory })
    }

    @Test
    fun spacersAreMarkers() {
        assertEquals(Marker, bouquetEntryKind("1:832:D:0:0:0:0:0:0:0:", atRoot = false))
    }

    @Test
    fun refsWithAPathOrAMediaTypeAreStreams() {
        assertEquals(
            Stream,
            bouquetEntryKind("4097:0:1:0:0:0:0:0:0:0:http%3a//tv.example/live:News", false)
        )
        assertEquals(
            Stream,
            bouquetEntryKind("1:0:1:0:0:0:0:0:0:0:http%3a//tv.example/live:News", false)
        )
    }

    @Test
    fun renamedServiceWithANameSuffixStaysAService() {
        assertEquals(
            Service,
            bouquetEntryKind("1:0:19:2B66:3F3:1:C00000:0:0:0::ZDF & Co <test>", false)
        )
    }

    private fun kinds(fixture: String, atRoot: Boolean): List<BouquetEntryKind> =
        ServiceParser.parse(loadWebFixture("bouqueteditor/$fixture"))
            .map { it.toBouquetEntry(atRoot).kind }
}
