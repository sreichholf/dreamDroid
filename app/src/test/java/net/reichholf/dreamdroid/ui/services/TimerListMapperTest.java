package net.reichholf.dreamdroid.ui.services;

import net.reichholf.dreamdroid.enigma.Timer;
import net.reichholf.dreamdroid.enigma.TimerParser;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TimerListMapperTest {
    @Test
    public void toExtendedHashMapCopiesTimerFields() throws Exception {
        InputStream in = getClass().getResourceAsStream("/web/timerlist.xml");
        assertNotNull(in);
        String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        List<Timer> timers = TimerParser.INSTANCE.parse(xml);
        Timer timer = timers.get(0);

        ExtendedHashMap map = TimerListMapper.toExtendedHashMap(timer);
        assertEquals(timer.getReference(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_REFERENCE));
        assertEquals(timer.getServiceName(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_SERVICE_NAME));
        assertEquals(timer.getEit(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_EIT));
        assertEquals(timer.getName(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_NAME));
        assertEquals(timer.getDescription(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DESCRIPTION));
        assertEquals(timer.getDescriptionExtended(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DESCRIPTION_EXTENDED));
        assertEquals(timer.getDisabled(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DISABLED));
        assertEquals(timer.getBegin(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_BEGIN));
        assertEquals(timer.getBeginReadable(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_BEGIN_READEABLE));
        assertEquals(timer.getEnd(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_END));
        assertEquals(timer.getEndReadable(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_END_READABLE));
        assertEquals(timer.getDuration(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DURATION));
        assertEquals(timer.getDurationReadable(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DURATION_READABLE));
        assertEquals(timer.getJustPlay(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_JUST_PLAY));
        assertEquals(timer.getAfterEvent(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_AFTER_EVENT));
        assertEquals(timer.getLocation(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_LOCATION));
        assertEquals(timer.getState(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_STATE));
        assertEquals(timer.getRepeated(), map.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_REPEATED));
    }
}
