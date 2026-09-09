package net.reichholf.dreamdroid.ui.current;

import net.reichholf.dreamdroid.enigma.CurrentService;
import net.reichholf.dreamdroid.enigma.CurrentServiceParser;
import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.ui.epg.EpgListMapper;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class CurrentServiceMapperTest {
    @Test
    public void eventToExtendedHashMapCopiesEventFields() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/getcurrent.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        CurrentService current = CurrentServiceParser.INSTANCE.parse(xml);
        assertNotNull(current);
        Event event = current.getNow();
        assertNotNull(event);

        ExtendedHashMap map = CurrentServiceMapper.eventToExtendedHashMap(event);
        assertNotNull(map);
        assertEquals(event.getEventId(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_ID));
        assertEquals(event.getTitle(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_TITLE));
        assertEquals(EpgListMapper.toExtendedHashMap(event).getString(
                net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START_READABLE),
                map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START_READABLE));
    }

    @Test
    public void eventToExtendedHashMapNullIsNull() {
        assertNull(CurrentServiceMapper.eventToExtendedHashMap(null));
    }

    @Test
    public void toExtendedHashMapCopiesServiceAndEvents() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/getcurrent.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        CurrentService current = CurrentServiceParser.INSTANCE.parse(xml);
        assertNotNull(current);

        ExtendedHashMap map = CurrentServiceMapper.toExtendedHashMap(current);
        ExtendedHashMap service = (ExtendedHashMap) map.get(
                net.reichholf.dreamdroid.helpers.enigma2.CurrentService.KEY_SERVICE);
        assertNotNull(service);
        assertEquals(current.getService().getReference(),
                service.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE));
        assertEquals(current.getService().getProvider(),
                service.getString(net.reichholf.dreamdroid.helpers.enigma2.CurrentService.KEY_SERVICE_PROVIDER));

        @SuppressWarnings("unchecked")
        ArrayList<ExtendedHashMap> events = (ArrayList<ExtendedHashMap>) map.get(
                net.reichholf.dreamdroid.helpers.enigma2.CurrentService.KEY_EVENTS);
        assertNotNull(events);
        assertEquals(2, events.size());
        assertEquals(current.getNow().getEventId(),
                events.get(0).getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_ID));
        assertEquals(current.getNext().getEventId(),
                events.get(1).getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_ID));
    }
}
