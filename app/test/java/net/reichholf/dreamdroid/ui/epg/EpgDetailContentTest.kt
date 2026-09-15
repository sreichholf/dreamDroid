package net.reichholf.dreamdroid.ui.epg

import net.reichholf.dreamdroid.enigma.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpgDetailContentTest {
    @Test
    fun emptyTitleReturnsNull() {
        val event = Event(
            title = "",
            startReadable = "20:00",
            durationReadable = "15",
            serviceName = "Das Erste HD"
        )
        assertNull(event.toEpgDetailContent("min"))
    }

    @Test
    fun naTitleReturnsNull() {
        val event = Event(
            title = "N/A",
            startReadable = "20:00",
            durationReadable = "15"
        )
        assertNull(event.toEpgDetailContent("min"))
    }

    @Test
    fun emptyTitleUnavailableKeepsSheetCopy() {
        val event = Event(
            title = "",
            startReadable = "20:00",
            durationReadable = "15",
            serviceName = "Das Erste HD",
            description = "News"
        )
        val content = event.toEpgDetailContentOrUnavailable("min", "Not available")
        assertEquals("Not available", content.title)
        assertEquals("Das Erste HD", content.serviceName)
        assertEquals("News", content.description)
        assertEquals("20:00 (15 min)", content.dateLine)
    }

    @Test
    fun populatedTitleMapsDateLine() {
        val event = Event(
            title = "Tagesschau",
            startReadable = "20:00",
            durationReadable = "15",
            serviceName = "Das Erste HD"
        )
        val content = event.toEpgDetailContent("min")
        assertNotNull(content)
        assertEquals("Tagesschau", content!!.title)
        assertEquals("20:00 (15 min)", content.dateLine)
    }
}
