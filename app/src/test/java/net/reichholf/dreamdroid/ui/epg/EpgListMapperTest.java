package net.reichholf.dreamdroid.ui.epg;

import net.reichholf.dreamdroid.enigma.Event;
import net.reichholf.dreamdroid.enigma.EventParser;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EpgListMapperTest {
    @Test
    public void toExtendedHashMapCopiesEventFields() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/epgservice.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        List<Event> events = EventParser.INSTANCE.parse(xml);
        Event event = events.get(0);

        ExtendedHashMap map = EpgListMapper.toExtendedHashMap(event);
        assertEquals(event.getEventId(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_ID));
        assertEquals(event.getTitle(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_TITLE));
        assertEquals(event.getStart(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START));
        assertEquals(event.getDuration(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_DURATION));
        assertEquals(event.getCurrentTime(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_CURRENT_TIME));
        assertEquals(event.getDescription(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_DESCRIPTION));
        assertEquals(event.getDescriptionExtended(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_DESCRIPTION_EXTENDED));
        assertEquals(event.getServiceReference(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_REFERENCE));
        assertEquals(event.getServiceName(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME));
        assertEquals(event.getStartReadable(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START_READABLE));
        assertEquals(event.getStartTimeReadable(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START_TIME_READABLE));
        assertEquals(event.getDurationReadable(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_DURATION_READABLE));
    }
}
