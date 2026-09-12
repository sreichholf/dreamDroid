package net.reichholf.dreamdroid.ui.epg

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.EventParser
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpgListMapperTest {
    @Test
    fun toExtendedHashMapCopiesEventFields() {
        val events = EventParser.parse(loadWebFixture("epgservice.xml"))
        val event = events[0]

        val map = EpgListMapper.toExtendedHashMap(event)
        assertEquals(event.eventId, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_ID))
        assertEquals(event.title, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_TITLE))
        assertEquals(event.start, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START))
        assertEquals(event.duration, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_DURATION))
        assertEquals(event.currentTime, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_CURRENT_TIME))
        assertEquals(
            event.description,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_DESCRIPTION),
        )
        assertEquals(
            event.descriptionExtended,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_DESCRIPTION_EXTENDED),
        )
        assertEquals(
            event.serviceReference,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_REFERENCE),
        )
        assertEquals(event.serviceName, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME))
        assertEquals(
            event.startReadable,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START_READABLE),
        )
        assertEquals(
            event.startTimeReadable,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START_TIME_READABLE),
        )
        assertEquals(
            event.durationReadable,
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_DURATION_READABLE),
        )
    }
}
