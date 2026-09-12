package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
                """<?xml version="1.0" encoding="UTF-8"?><e2frontendstatus></e2frontendstatus>""",
            ),
        )
    }
}
