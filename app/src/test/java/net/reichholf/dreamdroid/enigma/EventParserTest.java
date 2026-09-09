package net.reichholf.dreamdroid.enigma;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EventParserTest {
    @Test
    public void parsesEpgserviceFixtureIntoEventValues() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/epgservice.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        long started = System.nanoTime();
        List<Event> events = EventParser.INSTANCE.parse(xml);
        long elapsedNanos = System.nanoTime() - started;
        System.out.println("EventParser.parse nanos=" + elapsedNanos);

        assertEquals(2, events.size());

        Event first = events.get(0);
        assertEquals("39150", first.getEventId());
        assertEquals("Tagesschau", first.getTitle());
        assertEquals("1893456000", first.getStart());
        assertEquals("3600", first.getDuration());
        assertEquals("1893452400", first.getCurrentTime());
        assertEquals("Nachrichten", first.getDescription());
        assertTrue(first.getDescriptionExtended().contains("Die Nachrichten um 20 Uhr."));
        assertTrue(first.getDescriptionExtended().contains("\n"));
        assertFalse(first.getDescriptionExtended().contains("\u008A"));
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", first.getServiceReference());
        assertEquals("Das Erste HD", first.getServiceName());
        assertFalse(first.getStartReadable().isEmpty());
        assertFalse(first.getStartTimeReadable().isEmpty());
        assertEquals("60", first.getDurationReadable());

        Event second = events.get(1);
        assertEquals("39151", second.getEventId());
        assertEquals("N/A", second.getTitle());
        assertEquals("1893459600", second.getStart());
        assertEquals("1800", second.getDuration());
        assertEquals("30", second.getDurationReadable());
    }

    @Test
    public void emptyXmlYieldsNoEvents() {
        assertEquals(0, EventParser.INSTANCE.parse("").size());
    }

    @Test
    public void malformedXmlYieldsNoEvents() {
        assertEquals(0, EventParser.INSTANCE.parse("<e2eventlist><e2event>").size());
    }

    @Test
    public void stripsIllegalControlCharactersBeforeParse() {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e2eventlist>"
                + "<e2event>"
                + "<e2eventid>1</e2eventid>"
                + "<e2eventstart>1893456000</e2eventstart>"
                + "<e2eventduration>60</e2eventduration>"
                + "<e2eventcurrenttime>1893456000</e2eventcurrenttime>"
                + "<e2eventtitle>News</e2eventtitle>"
                + "<e2eventdescription>Has\u0001control</e2eventdescription>"
                + "<e2eventdescriptionextended>More</e2eventdescriptionextended>"
                + "<e2eventservicereference>1:0:1:1:1:1:0:0:0:0:</e2eventservicereference>"
                + "<e2eventservicename>TV</e2eventservicename>"
                + "</e2event>"
                + "</e2eventlist>";
        List<Event> events = EventParser.INSTANCE.parse(xml);
        assertEquals(1, events.size());
        assertEquals("News", events.get(0).getTitle());
        assertTrue(events.get(0).getDescription().contains("Has"));
        assertTrue(events.get(0).getDescription().contains("control"));
        assertFalse(events.get(0).getDescription().contains("\u0001"));
    }
}
