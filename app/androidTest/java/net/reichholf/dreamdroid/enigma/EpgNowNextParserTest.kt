package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpgNowNextParserTest {
    @Test
    fun pairsConsecutiveEventsFromFixture() {
        val rows = EpgNowNextParser.parse(loadWebFixture("epgnownext.xml"))

        assertEquals(3, rows.size)

        val first = rows[0]
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", first.serviceReference)
        assertEquals("Das Erste HD", first.serviceName)
        assertNotNull(first.now)
        assertNotNull(first.next)
        assertEquals("News Now", first.now!!.title)
        assertEquals("News Next", first.next!!.title)
        assertTrue(first.now!!.descriptionExtended.contains("\n"))

        val second = rows[1]
        assertEquals("ZDF HD", second.serviceName)
        assertEquals("Sport Now", second.now!!.title)
        assertEquals("Sport Next", second.next!!.title)

        val trailing = rows[2]
        assertEquals("Arte HD", trailing.serviceName)
        assertEquals("Trailing Only", trailing.now!!.title)
        assertNull(trailing.next)
    }

    @Test
    fun emptyXmlYieldsNoRows() {
        assertEquals(0, EpgNowNextParser.parse("").size)
    }

    @Test
    fun singleEventIsNowOnlyRow() {
        val events = EventParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2eventlist>
            <e2event>
            <e2eventid>1</e2eventid>
            <e2eventstart>1893456000</e2eventstart>
            <e2eventduration>60</e2eventduration>
            <e2eventcurrenttime>1893456000</e2eventcurrenttime>
            <e2eventtitle>Solo</e2eventtitle>
            <e2eventdescription/>
            <e2eventdescriptionextended/>
            <e2eventservicereference>1:0:1:1:1:1:0:0:0:0:</e2eventservicereference>
            <e2eventservicename>TV</e2eventservicename>
            </e2event>
            </e2eventlist>
            """.trimIndent(),
        )
        val rows = EpgNowNextParser.pairEvents(events)
        assertEquals(1, rows.size)
        assertEquals("Solo", rows[0].now!!.title)
        assertNull(rows[0].next)
    }

    @Test
    fun flatEpgNowMustNotPairUnrelatedServices() {
        // epgservice.xml is a flat two-event list for one service; treat like epgnow: one row each.
        val events = EventParser.parse(loadWebFixture("epgservice.xml"))
        assertEquals(2, events.size)
        // Client maps flat lists without pairing (see EnigmaClient.getEpgNowNext for non-NOWNEXT).
        val rows = events.map { event ->
            ServiceNowNext(event.serviceReference, event.serviceName, event, null)
        }
        assertEquals(2, rows.size)
        assertEquals("Tagesschau", rows[0].now!!.title)
        assertNull(rows[0].next)
        assertEquals("N/A", rows[1].now!!.title)
        assertNull(rows[1].next)
    }
}
