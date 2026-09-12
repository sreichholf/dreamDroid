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
class TimerParserTest {
    @Test
    fun parsesTimerlistFixtureIntoTimerValues() {
        val xml = loadWebFixture("timerlist.xml")
        val started = System.nanoTime()
        val timers = TimerParser.parse(xml)
        assertNotNull(timers)
        println("TimerParser.parse nanos=${System.nanoTime() - started}")

        assertEquals(2, timers!!.size)

        val first = timers[0]
        assertEquals("1:0:19:EF74:3F9:1:C00000:0:0:0:", first.reference)
        assertEquals("SAT.1 HD", first.serviceName)
        assertEquals("39350", first.eit)
        assertEquals("Navy CIS: L.A.", first.name)
        assertEquals("Kein Rauch ohne Feuer", first.description)
        assertTrue(first.descriptionExtended.contains("Feuerwehrmann"))
        assertEquals("0", first.disabled)
        assertEquals("1476644933", first.begin)
        assertEquals("1476649083", first.end)
        assertEquals("4150", first.duration)
        assertFalse(first.beginReadable.isEmpty())
        assertFalse(first.endReadable.isEmpty())
        assertEquals("69", first.durationReadable)
        assertEquals("1476644913", first.startPrepare)
        assertEquals("0", first.justPlay)
        assertEquals("3", first.afterEvent)
        assertEquals("None", first.location)
        assertEquals("", first.tags)
        assertTrue(first.logEntries.contains("record time changed"))
        assertEquals("0", first.state)
        assertEquals("0", first.repeated)
        assertEquals("False", first.canceled)
        assertEquals("1", first.toggleDisabled)

        val second = timers[1]
        assertEquals("Tagesschau", second.name)
        assertEquals("1", second.disabled)
        assertEquals("1", second.justPlay)
        assertEquals("127", second.repeated)
        assertEquals("news", second.tags)
        assertEquals("/hdd/movie/", second.location)
        assertEquals("60", second.durationReadable)
    }

    @Test
    fun emptyXmlYieldsNull() {
        assertNull(TimerParser.parse(""))
    }

    @Test
    fun malformedXmlYieldsNull() {
        assertNull(TimerParser.parse("<e2timerlist><e2timer>"))
    }

    @Test
    fun emptyTimerListYieldsEmptyList() {
        val timers = TimerParser.parse(
            """<?xml version="1.0" encoding="UTF-8"?><e2timerlist></e2timerlist>""",
        )
        assertNotNull(timers)
        assertEquals(0, timers!!.size)
    }

    @Test
    fun stripsIllegalControlCharactersBeforeParse() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2timerlist>
            <e2timer>
            <e2servicereference>1:0:1:1:1:1:0:0:0:0:</e2servicereference>
            <e2servicename>TV\u0001Channel</e2servicename>
            <e2eit>1</e2eit>
            <e2name>News</e2name>
            <e2description>Has\u0001control</e2description>
            <e2descriptionextended>More</e2descriptionextended>
            <e2disabled>0</e2disabled>
            <e2timebegin>1893456000</e2timebegin>
            <e2timeend>1893459600</e2timeend>
            <e2duration>3600</e2duration>
            <e2justplay>0</e2justplay>
            <e2afterevent>3</e2afterevent>
            <e2state>0</e2state>
            <e2repeated>0</e2repeated>
            </e2timer>
            </e2timerlist>
        """.trimIndent().replace("\\u0001", "\u0001")
        val timers = TimerParser.parse(xml)
        assertNotNull(timers)
        assertEquals(1, timers!!.size)
        assertEquals("News", timers[0].name)
        assertEquals("TVChannel", timers[0].serviceName)
        assertTrue(timers[0].description.contains("Has"))
        assertTrue(timers[0].description.contains("control"))
        assertFalse(timers[0].description.contains("\u0001"))
    }
}
