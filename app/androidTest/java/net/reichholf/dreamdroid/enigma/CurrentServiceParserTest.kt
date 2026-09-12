package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrentServiceParserTest {
    @Test
    fun parsesGetcurrentFixtureIntoCurrentService() {
        val xml = loadWebFixture("getcurrent.xml")
        val started = System.nanoTime()
        val current = CurrentServiceParser.parse(xml)
        println("CurrentServiceParser.parse nanos=${System.nanoTime() - started}")

        assertNotNull(current)
        val service = current!!.service
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", service.reference)
        assertEquals("Das Erste HD", service.name)
        assertEquals("ARD", service.provider)

        val now = current.now
        assertNotNull(now)
        assertEquals("39150", now!!.eventId)
        assertEquals("Tagesschau", now.title)
        assertEquals("1893456000", now.start)
        assertEquals("3600", now.duration)
        assertEquals("1893452400", now.currentTime)
        assertEquals("Nachrichten", now.description)
        assertTrue(now.descriptionExtended.contains("Die Nachrichten um 20 Uhr."))
        assertTrue(now.descriptionExtended.contains("\n"))
        assertFalse(now.descriptionExtended.contains("\u008A"))
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", now.serviceReference)
        assertEquals("Das Erste HD", now.serviceName)
        assertFalse(now.startReadable.isEmpty())
        assertEquals("60", now.durationReadable)

        val next = current.next
        assertNotNull(next)
        assertEquals("39151", next!!.eventId)
        assertEquals("N/A", next.title)
        assertEquals("1893459600", next.start)
        assertEquals("1800", next.duration)
        assertEquals("30", next.durationReadable)
    }

    @Test
    fun emptyXmlYieldsNull() {
        assertNull(CurrentServiceParser.parse(""))
    }

    @Test
    fun malformedXmlYieldsNull() {
        assertNull(CurrentServiceParser.parse("<e2currentserviceinformation><e2service>"))
    }

    @Test
    fun fallsBackToEventNameWhenTitleMissing() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2currentserviceinformation>
            <e2service>
            <e2servicereference>1:0:1:1:1:1:0:0:0:0:</e2servicereference>
            <e2servicename>TV</e2servicename>
            <e2providername>P</e2providername>
            </e2service>
            <e2event>
            <e2eventid>1</e2eventid>
            <e2eventstart>1893456000</e2eventstart>
            <e2eventduration>60</e2eventduration>
            <e2eventcurrenttime>1893456000</e2eventcurrenttime>
            <e2eventname>Legacy Title</e2eventname>
            <e2eventdescription/>
            <e2eventdescriptionextended/>
            <e2eventservicereference>1:0:1:1:1:1:0:0:0:0:</e2eventservicereference>
            <e2eventservicename>TV</e2eventservicename>
            </e2event>
            </e2currentserviceinformation>
        """.trimIndent()
        val current = CurrentServiceParser.parse(xml)
        assertNotNull(current)
        assertNotNull(current!!.now)
        assertEquals("Legacy Title", current.now!!.title)
    }
}
