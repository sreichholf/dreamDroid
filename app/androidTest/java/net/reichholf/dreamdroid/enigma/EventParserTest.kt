package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventParserTest {
    @Test
    fun parsesEpgserviceFixtureIntoEventValues() {
        val xml = loadWebFixture("epgservice.xml")
        val started = System.nanoTime()
        val events = EventParser.parse(xml)
        println("EventParser.parse nanos=${System.nanoTime() - started}")

        assertEquals(2, events.size)

        val first = events[0]
        assertEquals("39150", first.eventId)
        assertEquals("Tagesschau", first.title)
        assertEquals("1893456000", first.start)
        assertEquals("3600", first.duration)
        assertEquals("1893452400", first.currentTime)
        assertEquals("Nachrichten", first.description)
        assertTrue(first.descriptionExtended.contains("Die Nachrichten um 20 Uhr."))
        assertTrue(first.descriptionExtended.contains("\n"))
        assertFalse(first.descriptionExtended.contains("\u008A"))
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", first.serviceReference)
        assertEquals("Das Erste HD", first.serviceName)
        assertFalse(first.startReadable.isEmpty())
        assertFalse(first.startTimeReadable.isEmpty())
        assertEquals("60", first.durationReadable)

        val second = events[1]
        assertEquals("39151", second.eventId)
        assertEquals("N/A", second.title)
        assertEquals("1893459600", second.start)
        assertEquals("1800", second.duration)
        assertEquals("30", second.durationReadable)
    }

    @Test
    fun emptyXmlYieldsNoEvents() {
        assertEquals(0, EventParser.parse("").size)
    }

    @Test
    fun malformedXmlYieldsNoEvents() {
        assertEquals(0, EventParser.parse("<e2eventlist><e2event>").size)
    }

    @Test
    fun stripsIllegalControlCharactersBeforeParse() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2eventlist>
            <e2event>
            <e2eventid>1</e2eventid>
            <e2eventstart>1893456000</e2eventstart>
            <e2eventduration>60</e2eventduration>
            <e2eventcurrenttime>1893456000</e2eventcurrenttime>
            <e2eventtitle>News</e2eventtitle>
            <e2eventdescription>Has\u0001control</e2eventdescription>
            <e2eventdescriptionextended>More</e2eventdescriptionextended>
            <e2eventservicereference>1:0:1:1:1:1:0:0:0:0:</e2eventservicereference>
            <e2eventservicename>TV</e2eventservicename>
            </e2event>
            </e2eventlist>
        """.trimIndent().replace("\\u0001", "\u0001")
        val events = EventParser.parse(xml)
        assertEquals(1, events.size)
        assertEquals("News", events[0].title)
        assertTrue(events[0].description.contains("Has"))
        assertTrue(events[0].description.contains("control"))
        assertFalse(events[0].description.contains("\u0001"))
    }
}
