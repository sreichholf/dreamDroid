package net.reichholf.dreamdroid.enigma;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CurrentServiceParserTest {
    @Test
    public void parsesGetcurrentFixtureIntoCurrentService() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/getcurrent.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        long started = System.nanoTime();
        CurrentService current = CurrentServiceParser.INSTANCE.parse(xml);
        long elapsedNanos = System.nanoTime() - started;
        System.out.println("CurrentServiceParser.parse nanos=" + elapsedNanos);

        assertNotNull(current);
        Service service = current.getService();
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", service.getReference());
        assertEquals("Das Erste HD", service.getName());
        assertEquals("ARD", service.getProvider());

        Event now = current.getNow();
        assertNotNull(now);
        assertEquals("39150", now.getEventId());
        assertEquals("Tagesschau", now.getTitle());
        assertEquals("1893456000", now.getStart());
        assertEquals("3600", now.getDuration());
        assertEquals("1893452400", now.getCurrentTime());
        assertEquals("Nachrichten", now.getDescription());
        assertTrue(now.getDescriptionExtended().contains("Die Nachrichten um 20 Uhr."));
        assertTrue(now.getDescriptionExtended().contains("\n"));
        assertFalse(now.getDescriptionExtended().contains("\u008A"));
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", now.getServiceReference());
        assertEquals("Das Erste HD", now.getServiceName());
        assertFalse(now.getStartReadable().isEmpty());
        assertEquals("60", now.getDurationReadable());

        Event next = current.getNext();
        assertNotNull(next);
        assertEquals("39151", next.getEventId());
        assertEquals("N/A", next.getTitle());
        assertEquals("1893459600", next.getStart());
        assertEquals("1800", next.getDuration());
        assertEquals("30", next.getDurationReadable());
    }

    @Test
    public void emptyXmlYieldsNull() {
        assertNull(CurrentServiceParser.INSTANCE.parse(""));
    }

    @Test
    public void malformedXmlYieldsNull() {
        assertNull(CurrentServiceParser.INSTANCE.parse("<e2currentserviceinformation><e2service>"));
    }

    @Test
    public void fallsBackToEventNameWhenTitleMissing() {
        String xml = ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e2currentserviceinformation>"
                + "<e2service>"
                + "<e2servicereference>1:0:1:1:1:1:0:0:0:0:</e2servicereference>"
                + "<e2servicename>TV</e2servicename>"
                + "<e2providername>P</e2providername>"
                + "</e2service>"
                + "<e2event>"
                + "<e2eventid>1</e2eventid>"
                + "<e2eventstart>1893456000</e2eventstart>"
                + "<e2eventduration>60</e2eventduration>"
                + "<e2eventcurrenttime>1893456000</e2eventcurrenttime>"
                + "<e2eventname>Legacy Title</e2eventname>"
                + "<e2eventdescription/>"
                + "<e2eventdescriptionextended/>"
                + "<e2eventservicereference>1:0:1:1:1:1:0:0:0:0:</e2eventservicereference>"
                + "<e2eventservicename>TV</e2eventservicename>"
                + "</e2event>"
                + "</e2currentserviceinformation>";
        CurrentService current = CurrentServiceParser.INSTANCE.parse(xml);
        assertNotNull(current);
        assertNotNull(current.getNow());
        assertEquals("Legacy Title", current.getNow().getTitle());
    }
}
