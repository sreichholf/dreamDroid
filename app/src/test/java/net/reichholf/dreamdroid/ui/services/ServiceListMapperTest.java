package net.reichholf.dreamdroid.ui.services;

import net.reichholf.dreamdroid.enigma.EpgNowNextParser;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.enigma.ServiceNowNext;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ServiceListMapperTest {
    @Test
    public void mapsTypedNowNextRowsToComposeItems() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/epgnownext.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        List<ServiceNowNext> rows = EpgNowNextParser.INSTANCE.parse(xml);
        List<ServiceListItem> items = ServiceListMapperKt.serviceListItemsFromNowNext(rows);

        assertEquals(3, items.size());
        ServiceListItem first = items.get(0);
        assertEquals(ServiceRowKind.CHANNEL, first.getKind());
        assertEquals("Das Erste HD", first.getName());
        assertEquals("News Now", first.getNowTitle());
        assertEquals("News Next", first.getNextTitle());
        assertFalse(first.getNowStart().isEmpty());
        assertTrue(first.getProgressMax() > 0);
        assertTrue(first.getProgress() >= 0);

        ServiceListItem trailing = items.get(2);
        assertEquals("Trailing Only", trailing.getNowTitle());
        assertEquals("", trailing.getNextTitle());
    }

    @Test
    public void mapsMarkerAndDirectoryKinds() {
        ServiceNowNext marker = new ServiceNowNext(
                "1:64:1:0:0:0:0:0:0:0:",
                "Favorites",
                null,
                null
        );
        ServiceNowNext directory = new ServiceNowNext(
                "1:1:1:0:0:0:0:0:0:0:",
                "Subfolder",
                null,
                null
        );
        List<ServiceListItem> items = ServiceListMapperKt.serviceListItemsFromNowNext(
                java.util.Arrays.asList(marker, directory)
        );
        assertEquals(ServiceRowKind.MARKER, items.get(0).getKind());
        assertEquals(ServiceRowKind.DIRECTORY, items.get(1).getKind());
    }

    @Test
    public void combinedHashCarriesNowAndNextPrefixes() {
        Event now = new Event(
                "1", "Now", "100", "60", "100", "d", "x",
                "1:0:1:1:1:1:0:0:0:0:", "TV", "r", "t", "1"
        );
        Event next = new Event(
                "2", "Next", "160", "60", "100", "d2", "x2",
                "1:0:1:1:1:1:0:0:0:0:", "TV", "r2", "t2", "1"
        );
        ServiceNowNext row = new ServiceNowNext("1:0:1:1:1:1:0:0:0:0:", "TV", now, next);
        ExtendedHashMap map = ServiceListMapperKt.serviceNowNextToExtendedHashMap(row);
        assertEquals("Now", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_TITLE));
        assertEquals(
                "Next",
                map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.PREFIX_NEXT
                        + net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_TITLE)
        );
        assertEquals("TV", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME));
    }

    @Test
    public void fromExtendedHashMapRoundTripsNowAndNext() {
        Event now = new Event(
                "1", "Now", "100", "60", "100", "d", "x",
                "1:0:1:1:1:1:0:0:0:0:", "TV", "r", "t", "1"
        );
        Event next = new Event(
                "2", "Next", "160", "60", "100", "d2", "x2",
                "", "", "r2", "t2", "1"
        );
        ServiceNowNext row = new ServiceNowNext("1:0:1:1:1:1:0:0:0:0:", "TV", now, next);
        ExtendedHashMap map = ServiceListMapperKt.serviceNowNextToExtendedHashMap(row);
        ServiceNowNext back = ServiceListMapperKt.serviceNowNextFromExtendedHashMap(map);
        assertEquals(row.getServiceReference(), back.getServiceReference());
        assertEquals(row.getServiceName(), back.getServiceName());
        assertNotNull(back.getNow());
        assertEquals("Now", back.getNow().getTitle());
        assertEquals("1", back.getNow().getEventId());
        assertEquals("100", back.getNow().getStart());
        assertNotNull(back.getNext());
        assertEquals("Next", back.getNext().getTitle());
        assertEquals("2", back.getNext().getEventId());
        assertEquals("t2", back.getNext().getStartTimeReadable());
    }

    @Test
    public void emptyTypedListYieldsNoItems() {
        assertEquals(0, ServiceListMapperKt.serviceListItemsFromNowNext(Collections.emptyList()).size());
    }
}
