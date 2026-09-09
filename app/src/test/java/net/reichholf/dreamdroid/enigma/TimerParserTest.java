package net.reichholf.dreamdroid.enigma;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TimerParserTest {
    @Test
    public void parsesTimerlistFixtureIntoTimerValues() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/timerlist.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        long started = System.nanoTime();
        List<Timer> timers = TimerParser.INSTANCE.parse(xml);
        assertNotNull(timers);
        long elapsedNanos = System.nanoTime() - started;
        System.out.println("TimerParser.parse nanos=" + elapsedNanos);

        assertEquals(2, timers.size());

        Timer first = timers.get(0);
        assertEquals("1:0:19:EF74:3F9:1:C00000:0:0:0:", first.getReference());
        assertEquals("SAT.1 HD", first.getServiceName());
        assertEquals("39350", first.getEit());
        assertEquals("Navy CIS: L.A.", first.getName());
        assertEquals("Kein Rauch ohne Feuer", first.getDescription());
        assertTrue(first.getDescriptionExtended().contains("Feuerwehrmann"));
        assertEquals("0", first.getDisabled());
        assertEquals("1476644933", first.getBegin());
        assertEquals("1476649083", first.getEnd());
        assertEquals("4150", first.getDuration());
        assertFalse(first.getBeginReadable().isEmpty());
        assertFalse(first.getEndReadable().isEmpty());
        assertEquals("69", first.getDurationReadable());
        assertEquals("1476644913", first.getStartPrepare());
        assertEquals("0", first.getJustPlay());
        assertEquals("3", first.getAfterEvent());
        assertEquals("None", first.getLocation());
        assertEquals("", first.getTags());
        assertTrue(first.getLogEntries().contains("record time changed"));
        assertEquals("0", first.getState());
        assertEquals("0", first.getRepeated());
        assertEquals("False", first.getCanceled());
        assertEquals("1", first.getToggleDisabled());

        Timer second = timers.get(1);
        assertEquals("Tagesschau", second.getName());
        assertEquals("1", second.getDisabled());
        assertEquals("1", second.getJustPlay());
        assertEquals("127", second.getRepeated());
        assertEquals("news", second.getTags());
        assertEquals("/hdd/movie/", second.getLocation());
        assertEquals("60", second.getDurationReadable());
    }

    @Test
    public void emptyXmlYieldsNull() {
        assertEquals(null, TimerParser.INSTANCE.parse(""));
    }

    @Test
    public void malformedXmlYieldsNull() {
        assertEquals(null, TimerParser.INSTANCE.parse("<e2timerlist><e2timer>"));
    }

    @Test
    public void emptyTimerListYieldsEmptyList() {
        List<Timer> timers = TimerParser.INSTANCE.parse(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e2timerlist></e2timerlist>");
        assertNotNull(timers);
        assertEquals(0, timers.size());
    }

    @Test
    public void stripsIllegalControlCharactersBeforeParse() {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e2timerlist>"
                + "<e2timer>"
                + "<e2servicereference>1:0:1:1:1:1:0:0:0:0:</e2servicereference>"
                + "<e2servicename>TV\u0001Channel</e2servicename>"
                + "<e2eit>1</e2eit>"
                + "<e2name>News</e2name>"
                + "<e2description>Has\u0001control</e2description>"
                + "<e2descriptionextended>More</e2descriptionextended>"
                + "<e2disabled>0</e2disabled>"
                + "<e2timebegin>1893456000</e2timebegin>"
                + "<e2timeend>1893459600</e2timeend>"
                + "<e2duration>3600</e2duration>"
                + "<e2justplay>0</e2justplay>"
                + "<e2afterevent>3</e2afterevent>"
                + "<e2state>0</e2state>"
                + "<e2repeated>0</e2repeated>"
                + "</e2timer>"
                + "</e2timerlist>";
        List<Timer> timers = TimerParser.INSTANCE.parse(xml);
        assertNotNull(timers);
        assertEquals(1, timers.size());
        assertEquals("News", timers.get(0).getName());
        assertEquals("TVChannel", timers.get(0).getServiceName());
        assertTrue(timers.get(0).getDescription().contains("Has"));
        assertTrue(timers.get(0).getDescription().contains("control"));
        assertFalse(timers.get(0).getDescription().contains("\u0001"));
    }
}
