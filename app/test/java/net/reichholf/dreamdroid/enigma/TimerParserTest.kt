package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
        assertNull(first.vps)
        assertNull(second.vps)
    }

    @Test
    fun readsTheVpsPluginFields() {
        val timers = TimerParser.parse(loadWebFixture("vps/timerlist.xml"))!!

        assertEquals(
            listOf(
                TimerVps(VpsMode.Off),
                TimerVps(VpsMode.Safe),
                TimerVps(VpsMode.Overwrite, 1_893_611_100L)
            ),
            timers.map { it.vps }
        )
        assertEquals("Navy CIS: L.A.", timers[0].name)
        assertEquals("1", timers[0].toggleDisabled)
    }

    @Test
    fun readsVpsFlagsAsDigits() {
        val vps = TimerParser.parse(vpsTimer(enabled = "1", overwrite = "0", time = "0"))!!
            .single().vps

        assertEquals(TimerVps(VpsMode.Safe), vps)
    }

    @Test
    fun vpsOverwriteWithoutEnabledIsOff() {
        val vps = TimerParser.parse(vpsTimer(enabled = "False", overwrite = "True", time = "-1"))!!
            .single().vps

        assertEquals(TimerVps(VpsMode.Off), vps)
    }

    private fun vpsTimer(enabled: String, overwrite: String, time: String): String =
        "<e2timerlist><e2timer><e2name>News</e2name>" +
            "<e2vpsplugin_enabled>$enabled</e2vpsplugin_enabled>" +
            "<e2vpsplugin_overwrite>$overwrite</e2vpsplugin_overwrite>" +
            "<e2vpsplugin_time>$time</e2vpsplugin_time>" +
            "</e2timer></e2timerlist>"

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
            """<?xml version="1.0" encoding="UTF-8"?><e2timerlist></e2timerlist>"""
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

    @Test
    fun readsCancledTypoTag() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2timerlist>
            <e2timer>
            <e2servicereference>1:0:1:1:1:1:0:0:0:0:</e2servicereference>
            <e2servicename>TV</e2servicename>
            <e2name>News</e2name>
            <e2timebegin>1893456000</e2timebegin>
            <e2timeend>1893459600</e2timeend>
            <e2duration>3600</e2duration>
            <e2cancled>True</e2cancled>
            </e2timer>
            </e2timerlist>
        """.trimIndent()
        val timers = TimerParser.parse(xml)
        assertNotNull(timers)
        assertEquals(1, timers!!.size)
        assertEquals("True", timers[0].canceled)
    }
}
