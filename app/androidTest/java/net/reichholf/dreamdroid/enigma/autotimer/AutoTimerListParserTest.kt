package net.reichholf.dreamdroid.enigma.autotimer

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalTime
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `/autotimer` list on the device's own XML parser; the JVM tests run the fallback parser.
 * Most of the list is in attributes, which only the AutoTimer parser reads.
 */
@RunWith(AndroidJUnit4::class)
class AutoTimerListParserTest {
    @Test
    fun readsTheCapturedLists() {
        val full = readable(parse("list_disabled_full.xml").single())
        assertEquals(AutoTimerId(1), full.id)
        assertEquals("dreamDroid test Wilsberg", full.settings.name)
        assertEquals(
            listOf("ZDF HD", "zdf_neo HD", "Favourites (TV)"),
            full.settings.targets.map { it.name }
        )
        assertEquals(Offset(5, 10), full.settings.offset)
        assertEquals(
            Filters(title = listOf("Vorschau"), description = listOf("Wiederholung")),
            full.settings.exclude
        )

        val enabled = readable(parse("list_enabled.xml").single())
        assertTrue(enabled.settings.enabled)
        assertEquals(
            ClockWindow(LocalTime.of(20, 0), LocalTime.of(23, 0)),
            enabled.settings.timeWindow
        )

        assertEquals(emptyList<AutoTimerEntry>(), parse("list_empty.xml"))
    }

    @Test
    fun attributesDecodeEntities() {
        val xml = """<?xml version="1.0" ?><autotimer version="8"><defaults id="-1"></defaults>""" +
            """<timer name="Tom &amp; Jerry" match="&quot;Tom&quot; &lt;3" enabled="yes" """ +
            """id="4"></timer></autotimer>"""

        val settings = readable(AutoTimerListParser.parse(xml)!!.entries.single()).settings

        assertEquals("Tom & Jerry", settings.name)
        assertEquals("\"Tom\" <3", settings.match)
    }

    @Test
    fun aSimpleResultIsNotAList() {
        val xml = "<e2simplexmlresult><e2state>False</e2state>" +
            "<e2statetext>Couldn't load config file!</e2statetext></e2simplexmlresult>"

        assertNull(AutoTimerListParser.parse(xml))
    }

    private fun parse(fixture: String): List<AutoTimerEntry> =
        AutoTimerListParser.parse(loadWebFixture("autotimer/$fixture"))!!.entries

    private fun readable(entry: AutoTimerEntry): AutoTimer =
        (entry as AutoTimerEntry.Readable).autoTimer
}
