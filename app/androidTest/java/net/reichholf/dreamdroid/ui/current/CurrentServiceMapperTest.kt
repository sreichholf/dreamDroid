package net.reichholf.dreamdroid.ui.current

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.CurrentServiceParser
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.testutil.loadWebFixture
import net.reichholf.dreamdroid.ui.epg.EpgListMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CurrentServiceMapperTest {
    @Test
    fun eventToExtendedHashMapCopiesEventFields() {
        val current = CurrentServiceParser.parse(loadWebFixture("getcurrent.xml"))
        assertNotNull(current)
        val event = current!!.now
        assertNotNull(event)

        val map = CurrentServiceMapper.eventToExtendedHashMap(event)
        assertNotNull(map)
        assertEquals(event!!.eventId, map!!.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_ID))
        assertEquals(event.title, map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_TITLE))
        assertEquals(
            EpgListMapper.toExtendedHashMap(event).getString(
                net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START_READABLE,
            ),
            map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_START_READABLE),
        )
    }

    @Test
    fun eventToExtendedHashMapNullIsNull() {
        assertNull(CurrentServiceMapper.eventToExtendedHashMap(null))
    }

    @Test
    fun toExtendedHashMapCopiesServiceAndEvents() {
        val current = CurrentServiceParser.parse(loadWebFixture("getcurrent.xml"))
        assertNotNull(current)

        val map = CurrentServiceMapper.toExtendedHashMap(current)
        val service = map[net.reichholf.dreamdroid.helpers.enigma2.CurrentService.KEY_SERVICE] as ExtendedHashMap
        assertNotNull(service)
        assertEquals(
            current!!.service.reference,
            service.getString(net.reichholf.dreamdroid.helpers.enigma2.Service.KEY_REFERENCE),
        )
        assertEquals(
            current.service.provider,
            service.getString(net.reichholf.dreamdroid.helpers.enigma2.CurrentService.KEY_SERVICE_PROVIDER),
        )

        @Suppress("UNCHECKED_CAST")
        val events = map[net.reichholf.dreamdroid.helpers.enigma2.CurrentService.KEY_EVENTS]
            as ArrayList<ExtendedHashMap>
        assertNotNull(events)
        assertEquals(2, events.size)
        assertEquals(
            current.now!!.eventId,
            events[0].getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_ID),
        )
        assertEquals(
            current.next!!.eventId,
            events[1].getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_ID),
        )
    }
}
