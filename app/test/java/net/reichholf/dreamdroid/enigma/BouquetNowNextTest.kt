package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.helpers.NameValuePair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BouquetNowNextTest {
    private val dasErste = "1:0:1:6DCA:44C:1:C00000:0:0:0:"
    private val fipReggae =
        "4097:0:2:0:0:0:0:0:0:0:https%3a//icecast.radiofrance.fr/fipreggae-hifi.aac:FIP Reggae"
    private val marker = "1:64:1:0:0:0:0:0:0:0:"
    private val directory = "1:1:0:0:0:0:0:0:0:0:"
    private val epgOnly = "1:0:1:999:1:1:0:0:0:0:"

    @Test
    fun webradioWithoutEpgStaysNamedInBouquetOrder() {
        val roster = listOf(
            Service(fipReggae, "FIP Reggae"),
            Service(marker, "--------"),
            Service(directory, "Providers"),
            Service(dasErste, "Das Erste HD")
        )
        val epg = listOf(
            ServiceNowNext(
                serviceReference = epgOnly,
                serviceName = "Not in bouquet",
                now = event("Extra", epgOnly),
                next = event("Extra next", epgOnly)
            ),
            ServiceNowNext(
                serviceReference = dasErste,
                serviceName = "Name from EPG",
                now = event("Tagesschau", dasErste),
                next = event("Sport", dasErste)
            )
        )

        val rows = mergeBouquetNowNext(roster, epg)

        assertEquals(4, rows.size)
        assertEquals(fipReggae, rows[0].serviceReference)
        assertEquals("FIP Reggae", rows[0].serviceName)
        assertNull(rows[0].now)
        assertNull(rows[0].next)
        assertEquals(marker, rows[1].serviceReference)
        assertEquals("--------", rows[1].serviceName)
        assertNull(rows[1].now)
        assertNull(rows[1].next)
        assertEquals(directory, rows[2].serviceReference)
        assertEquals("Providers", rows[2].serviceName)
        assertNull(rows[2].now)
        assertEquals("Das Erste HD", rows[3].serviceName)
        assertEquals("Tagesschau", rows[3].now?.title)
        assertEquals("Sport", rows[3].next?.title)
    }

    @Test
    fun rosterNameWinsWhenEpgNameDiffers() {
        val rows = mergeBouquetNowNext(
            listOf(Service(fipReggae, "FIP Reggae")),
            listOf(
                ServiceNowNext(
                    serviceReference = fipReggae,
                    serviceName = "Other",
                    now = event("Live", fipReggae),
                    next = null
                )
            )
        )

        assertEquals(1, rows.size)
        assertEquals("FIP Reggae", rows[0].serviceName)
        assertEquals("Live", rows[0].now?.title)
        assertNull(rows[0].next)
    }

    @Test
    fun emptyRosterOmitsEpgOnlyRows() {
        val rows = mergeBouquetNowNext(
            emptyList(),
            listOf(ServiceNowNext(dasErste, "Das Erste HD", event("Now", dasErste), null))
        )
        assertEquals(emptyList<ServiceNowNext>(), rows)
    }

    @Test
    fun firstEpgRowWinsForDuplicateReference() {
        val rows = mergeBouquetNowNext(
            listOf(Service(dasErste, "Das Erste HD")),
            listOf(
                ServiceNowNext(dasErste, "A", event("First", dasErste), null),
                ServiceNowNext(dasErste, "B", event("Second", dasErste), null)
            )
        )
        assertEquals("First", rows[0].now?.title)
    }

    @Test
    fun blankReferenceDoesNotTakeBlankEpgRow() {
        val rows = mergeBouquetNowNext(
            listOf(Service("", "Nameless")),
            listOf(ServiceNowNext("", "Ghost", event("Nope", ""), null))
        )
        assertEquals(1, rows.size)
        assertEquals("Nameless", rows[0].serviceName)
        assertEquals("", rows[0].serviceReference)
        assertNull(rows[0].now)
        assertNull(rows[0].next)
    }

    @Test
    fun favouritesBouquetAsksGetServicesWithSRef() {
        val favourites =
            "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet"
        val params = serviceListParams(listOf(NameValuePair("bRef", favourites)))
        assertEquals("sRef", params.single().key())
        assertEquals(favourites, params.single().value())
    }

    @Test
    fun directoryDrillDownKeepsSRef() {
        val folder = "1:0:1:0:0:0:0:0:0:0:FROM PROVIDERS"
        val params = serviceListParams(listOf(NameValuePair("sRef", folder)))
        assertEquals("sRef", params.single().key())
        assertEquals(folder, params.single().value())
    }

    @Test
    fun explicitSRefWinsOverBouquetParam() {
        val params = serviceListParams(
            listOf(
                NameValuePair("bRef", "1:7:1:0:0:0:0:0:0:0:"),
                NameValuePair("sRef", "1:0:1:6DCA:44C:1:C00000:0:0:0:")
            )
        )
        assertEquals("sRef", params.single().key())
        assertEquals("1:0:1:6DCA:44C:1:C00000:0:0:0:", params.single().value())
    }

    @Test
    fun missingReferenceLeavesParamsUnchanged() {
        val original = listOf(NameValuePair("other", "x"))
        assertEquals(original, serviceListParams(original))
        assertEquals(emptyList<NameValuePair>(), serviceListParams(emptyList()))
    }

    private fun event(title: String, ref: String): Event = Event(
        title = title,
        serviceReference = ref,
        serviceName = "From EPG"
    )
}
