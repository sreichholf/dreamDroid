package net.reichholf.dreamdroid.room

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpgSearchKeyTest {
    @Test
    fun foldsUmlautsSharpSAndDecomposedForms() {
        assertEquals(epgSearchKey("ärger"), epgSearchKey("ÄRGER"))
        assertEquals(epgSearchKey("strasse"), epgSearchKey("Straße"))
        assertEquals(epgSearchKey("München"), epgSearchKey("München"))
        assertEquals(epgSearchKey("ÉTÉ"), epgSearchKey("été"))
    }

    @Test
    fun entityKeyFollowsTheTitle() {
        val entity = EpgEventEntity(
            profileId = 1,
            bouquetRef = "b",
            serviceRef = "s",
            eventId = "1",
            start = 0L,
            duration = 60L,
            title = "Tatort: München",
            description = "",
            descriptionExtended = "",
            serviceName = ""
        )

        assertEquals("tatort: münchen", entity.titleKey)
    }
}
