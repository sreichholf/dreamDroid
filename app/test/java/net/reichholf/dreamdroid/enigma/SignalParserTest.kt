package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SignalParserTest {
    @Test
    fun parsesSignalFixtureWithTypoAgcTag() {
        val signal = SignalParser.parse(loadWebFixture("signal.xml"))
        assertNotNull(signal)
        assertFalse(signal!!.isEmpty())

        assertEquals("12.50 dB", signal.snrDbRaw)
        assertEquals("63 %", signal.snrRaw)
        assertEquals("0", signal.berRaw)
        assertEquals("73 %", signal.agcRaw)
        assertEquals(63, signal.snrPercent)
        assertEquals(12.50, signal.snrDb, 0.001)
    }

    @Test
    fun acceptsCorrectedAgcTag() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2frontendstatus>
            <e2snrdb>8.0 dB</e2snrdb>
            <e2snr>40 %</e2snr>
            <e2ber>1</e2ber>
            <e2agc>50 %</e2agc>
            </e2frontendstatus>
        """.trimIndent()
        val signal = SignalParser.parse(xml)
        assertNotNull(signal)
        assertEquals("50 %", signal!!.agcRaw)
        assertEquals(40, signal.snrPercent)
    }

    @Test
    fun emptyXmlYieldsNull() {
        assertNull(SignalParser.parse(""))
    }

    @Test
    fun emptyFrontendStatusYieldsNull() {
        assertNull(
            SignalParser.parse(
                """<?xml version="1.0" encoding="UTF-8"?><e2frontendstatus></e2frontendstatus>"""
            )
        )
    }

    @Test
    fun stripsNamespacePrefixOnTags() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2:e2frontendstatus xmlns:e2="http://www.dreambox.net/">
            <e2:e2snrdb>1 dB</e2:e2snrdb>
            <e2:e2snr>10 %</e2:e2snr>
            <e2:e2ber>0</e2:e2ber>
            <e2:e2acg>20 %</e2:e2acg>
            </e2:e2frontendstatus>
        """.trimIndent()
        val signal = SignalParser.parse(xml)
        assertNotNull(signal)
        assertEquals("20 %", signal!!.agcRaw)
        assertEquals(10, signal.snrPercent)
    }

    @Test
    fun replacesNbspEntitiesDuringSanitize() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2frontendstatus>
            <e2snrdb>1&nbsp;dB</e2snrdb>
            <e2snr>10 %</e2snr>
            <e2ber>0</e2ber>
            <e2agc>20 %</e2agc>
            </e2frontendstatus>
        """.trimIndent()
        val signal = SignalParser.parse(xml)
        assertNotNull(signal)
        assertEquals("1 dB", signal!!.snrDbRaw)
    }

    @Test
    fun stripsFooPrefixedSnrTag() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2frontendstatus>
            <e2snrdb>1 dB</e2snrdb>
            <foo:e2snr xmlns:foo="urn:x">10 %</foo:e2snr>
            <e2ber>0</e2ber>
            <e2agc>20 %</e2agc>
            </e2frontendstatus>
        """.trimIndent()
        val signal = SignalParser.parse(xml)
        assertNotNull(signal)
        assertEquals("10 %", signal!!.snrRaw)
        assertEquals(10, signal.snrPercent)
    }

    @Test
    fun concatenatesSplitTextNodesForSnr() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <e2frontendstatus>
            <e2snrdb>1 dB</e2snrdb>
            <e2snr>6<!--split-->3 %</e2snr>
            <e2ber>0</e2ber>
            <e2acg>20 %</e2acg>
            </e2frontendstatus>
        """.trimIndent()
        val signal = SignalParser.parse(xml)
        assertNotNull(signal)
        assertEquals("63 %", signal!!.snrRaw)
        assertEquals(63, signal.snrPercent)
        assertEquals("20 %", signal.agcRaw)
    }
}
