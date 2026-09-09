package net.reichholf.dreamdroid.enigma;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SignalParserTest {
    @Test
    public void parsesSignalFixtureWithTypoAgcTag() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/signal.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        Signal signal = SignalParser.INSTANCE.parse(xml);
        assertNotNull(signal);
        assertFalse(signal.isEmpty());

        assertEquals("12.50 dB", signal.getSnrDbRaw());
        assertEquals("63 %", signal.getSnrRaw());
        assertEquals("0", signal.getBerRaw());
        assertEquals("73 %", signal.getAgcRaw());
        assertEquals(63, signal.getSnrPercent());
        assertEquals(12.50, signal.getSnrDb(), 0.001);
    }

    @Test
    public void acceptsCorrectedAgcTag() {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e2frontendstatus>"
                + "<e2snrdb>8.0 dB</e2snrdb>"
                + "<e2snr>40 %</e2snr>"
                + "<e2ber>1</e2ber>"
                + "<e2agc>50 %</e2agc>"
                + "</e2frontendstatus>";
        Signal signal = SignalParser.INSTANCE.parse(xml);
        assertNotNull(signal);
        assertEquals("50 %", signal.getAgcRaw());
        assertEquals(40, signal.getSnrPercent());
    }

    @Test
    public void emptyXmlYieldsNull() {
        assertNull(SignalParser.INSTANCE.parse(""));
    }

    @Test
    public void emptyFrontendStatusYieldsNull() {
        assertNull(SignalParser.INSTANCE.parse(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e2frontendstatus></e2frontendstatus>"));
    }
}
