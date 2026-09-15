package net.reichholf.dreamdroid.ui.epg

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.EventParser
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpgListMapperTest {
    @Test
    fun parsesTypedEventFields() {
        val events = EventParser.parse(loadWebFixture("epgservice.xml"))
        val event = events[0]

        assertEquals("39150", event.eventId)
        assertEquals("Tagesschau", event.title)
        assertEquals("1893456000", event.start)
        assertEquals("3600", event.duration)
        assertEquals("1893452400", event.currentTime)
        assertEquals("Nachrichten", event.description)
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", event.serviceReference)
        assertEquals("Das Erste HD", event.serviceName)
        assertTrue(event.startReadable.isNotEmpty())
        assertTrue(event.startTimeReadable.isNotEmpty())
        assertTrue(event.durationReadable.isNotEmpty())
        assertTrue(event.descriptionExtended.contains("Die Nachrichten um 20 Uhr."))
    }
}
