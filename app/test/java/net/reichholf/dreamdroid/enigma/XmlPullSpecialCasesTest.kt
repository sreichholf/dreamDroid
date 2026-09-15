package net.reichholf.dreamdroid.enigma

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

/**
 * Locks Enigma2 XML special cases that must not regress after the SAX → XmlPull swap.
 */
class XmlPullSpecialCasesTest {
    @Test
    fun signalAcceptsAcgTypoAndAgc() {
        val acg = SignalParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2frontendstatus>
            <e2snrdb>1 dB</e2snrdb>
            <e2snr>10 %</e2snr>
            <e2ber>0</e2ber>
            <e2acg>33 %</e2acg>
            </e2frontendstatus>
            """.trimIndent()
        )
        assertEquals("33 %", acg!!.agcRaw)

        val agc = SignalParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2frontendstatus>
            <e2snrdb>1 dB</e2snrdb>
            <e2snr>10 %</e2snr>
            <e2ber>0</e2ber>
            <e2agc>44 %</e2agc>
            </e2frontendstatus>
            """.trimIndent()
        )
        assertEquals("44 %", agc!!.agcRaw)
    }

    @Test
    fun timerReadsCancledSpelling() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2timerlist><e2timer>
            <e2name>T</e2name>
            <e2cancled>True</e2cancled>
            </e2timer></e2timerlist>
        """.trimIndent()
        assertEquals("True", TimerParser.parse(xml)!![0].canceled)
    }

    @Test
    fun currentServiceFallsBackToEventName() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2currentserviceinformation>
            <e2service>
            <e2servicereference>1:0:1:1:1:1:0:0:0:0:</e2servicereference>
            <e2servicename>TV</e2servicename>
            </e2service>
            <e2event>
            <e2eventid>1</e2eventid>
            <e2eventstart>1893456000</e2eventstart>
            <e2eventduration>60</e2eventduration>
            <e2eventname>FromName</e2eventname>
            <e2eventservicereference>1:0:1:1:1:1:0:0:0:0:</e2eventservicereference>
            <e2eventservicename>TV</e2eventservicename>
            </e2event>
            </e2currentserviceinformation>
        """.trimIndent()
        assertEquals("FromName", CurrentServiceParser.parse(xml)!!.now!!.title)
    }

    @Test
    fun sanitizeReplacesNbspAndControlChars() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2frontendstatus>
            <e2snrdb>1&nbsp;dB</e2snrdb>
            <e2snr>1\u00010 %</e2snr>
            <e2ber>0</e2ber>
            <e2agc>20 %</e2agc>
            </e2frontendstatus>
        """.trimIndent().replace("\\u0001", "\u0001")
        val signal = SignalParser.parse(xml)
        assertNotNull(signal)
        assertEquals("1 dB", signal!!.snrDbRaw)
        assertEquals("10 %", signal.snrRaw)
        assertFalse(signal.snrRaw.contains("\u0001"))
    }

    @Test
    fun pullParserEmitsTwoTextEventsAcrossComment() {
        val parser = EnigmaXmlPullParser()
        parser.setInput(StringReader("<e2snr>6<!--x-->3 %</e2snr>"))
        val chunks = ArrayList<String>()
        var event = parser.eventType
        var inSnr = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> inSnr = parser.localTag() == "e2snr"
                XmlPullParser.TEXT -> if (inSnr) chunks.add(parser.text ?: "")
                XmlPullParser.END_TAG -> inSnr = false
            }
            event = parser.next()
        }
        assertTrue(chunks.size >= 2)
        assertEquals("63 %", chunks.joinToString(""))
    }

    @Test
    fun namespacedFooE2snrIsStrippedToLocalTag() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2frontendstatus>
            <e2snrdb>1 dB</e2snrdb>
            <foo:e2snr>55 %</foo:e2snr>
            <e2ber>0</e2ber>
            <e2agc>20 %</e2agc>
            </e2frontendstatus>
        """.trimIndent()
        val signal = SignalParser.parse(xml)
        assertNotNull(signal)
        assertEquals("55 %", signal!!.snrRaw)
        assertEquals(55, signal.snrPercent)
    }

    @Test
    fun movieNoneFilesizeBecomesZeroMb() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2movielist><e2movie>
            <e2title>T</e2title>
            <e2filesize>None</e2filesize>
            </e2movie></e2movielist>
        """.trimIndent()
        val movies = MovieParser.parse(xml)
        assertEquals("None", movies!![0].fileSize)
        assertEquals("0 MB", movies[0].fileSizeReadable)
    }

    @Test
    fun emptyMovieXmlIsNullNotEmptyList() {
        assertNull(MovieParser.parse(""))
    }

    @Test
    fun emptyEventXmlIsEmptyList() {
        assertEquals(0, EventParser.parse("").size)
    }

    @Test
    fun serviceParseFailureIsEmptyList() {
        assertEquals(0, ServiceParser.parse("<e2servicelist><e2service>").size)
    }
}
