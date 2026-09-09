package net.reichholf.dreamdroid.enigma;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EpgNowNextParserTest {
    @Test
    public void pairsConsecutiveEventsFromFixture() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/epgnownext.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        List<ServiceNowNext> rows = EpgNowNextParser.INSTANCE.parse(xml);

        assertEquals(3, rows.size());

        ServiceNowNext first = rows.get(0);
        assertEquals("1:0:1:6DCA:44D:1:C00000:0:0:0:", first.getServiceReference());
        assertEquals("Das Erste HD", first.getServiceName());
        assertNotNull(first.getNow());
        assertNotNull(first.getNext());
        assertEquals("News Now", first.getNow().getTitle());
        assertEquals("News Next", first.getNext().getTitle());
        assertTrue(first.getNow().getDescriptionExtended().contains("\n"));

        ServiceNowNext second = rows.get(1);
        assertEquals("ZDF HD", second.getServiceName());
        assertEquals("Sport Now", second.getNow().getTitle());
        assertEquals("Sport Next", second.getNext().getTitle());

        ServiceNowNext trailing = rows.get(2);
        assertEquals("Arte HD", trailing.getServiceName());
        assertEquals("Trailing Only", trailing.getNow().getTitle());
        assertNull(trailing.getNext());
    }

    @Test
    public void emptyXmlYieldsNoRows() {
        assertEquals(0, EpgNowNextParser.INSTANCE.parse("").size());
    }

    @Test
    public void singleEventIsNowOnlyRow() {
        List<Event> events = EventParser.INSTANCE.parse(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e2eventlist>"
                + "<e2event>"
                + "<e2eventid>1</e2eventid>"
                + "<e2eventstart>1893456000</e2eventstart>"
                + "<e2eventduration>60</e2eventduration>"
                + "<e2eventcurrenttime>1893456000</e2eventcurrenttime>"
                + "<e2eventtitle>Solo</e2eventtitle>"
                + "<e2eventdescription/>"
                + "<e2eventdescriptionextended/>"
                + "<e2eventservicereference>1:0:1:1:1:1:0:0:0:0:</e2eventservicereference>"
                + "<e2eventservicename>TV</e2eventservicename>"
                + "</e2event>"
                + "</e2eventlist>");
        List<ServiceNowNext> rows = EpgNowNextParser.INSTANCE.pairEvents(events);
        assertEquals(1, rows.size());
        assertEquals("Solo", rows.get(0).getNow().getTitle());
        assertNull(rows.get(0).getNext());
    }
}
