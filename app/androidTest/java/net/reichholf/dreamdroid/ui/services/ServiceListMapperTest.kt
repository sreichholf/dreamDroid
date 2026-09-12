package net.reichholf.dreamdroid.ui.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.EpgNowNextParser
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceListMapperTest {
    @Test
    fun mapsTypedNowNextRowsToComposeItems() {
        val rows = EpgNowNextParser.parse(loadWebFixture("epgnownext.xml"))
        val items = serviceListItemsFromNowNext(rows)

        assertEquals(3, items.size)
        val first = items[0]
        assertEquals(ServiceRowKind.CHANNEL, first.kind)
        assertEquals("Das Erste HD", first.name)
        assertEquals("News Now", first.nowTitle)
        assertEquals("News Next", first.nextTitle)
        assertFalse(first.nowStart.isEmpty())
        assertTrue(first.progressMax > 0)
        assertTrue(first.progress >= 0)

        val trailing = items[2]
        assertEquals("Trailing Only", trailing.nowTitle)
        assertEquals("", trailing.nextTitle)
    }

    @Test
    fun mapsMarkerAndDirectoryKinds() {
        val marker = ServiceNowNext(
            "1:64:1:0:0:0:0:0:0:0:",
            "Favorites",
            null,
            null,
        )
        val directory = ServiceNowNext(
            "1:1:1:0:0:0:0:0:0:0:",
            "Subfolder",
            null,
            null,
        )
        val items = serviceListItemsFromNowNext(listOf(marker, directory))
        assertEquals(ServiceRowKind.MARKER, items[0].kind)
        assertEquals(ServiceRowKind.DIRECTORY, items[1].kind)
    }

    @Test
    fun combinedHashCarriesNowAndNextPrefixes() {
        val now = Event(
            "1", "Now", "100", "60", "100", "d", "x",
            "1:0:1:1:1:1:0:0:0:0:", "TV", "r", "t", "1",
        )
        val next = Event(
            "2", "Next", "160", "60", "100", "d2", "x2",
            "1:0:1:1:1:1:0:0:0:0:", "TV", "r2", "t2", "1",
        )
        val row = ServiceNowNext("1:0:1:1:1:1:0:0:0:0:", "TV", now, next)
        val map = serviceNowNextToExtendedHashMap(row)
        assertEquals("Now", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_TITLE))
        assertEquals(
            "Next",
            map.getString(
                net.reichholf.dreamdroid.helpers.enigma2.Event.PREFIX_NEXT +
                    net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_EVENT_TITLE,
            ),
        )
        assertEquals("TV", map.getString(net.reichholf.dreamdroid.helpers.enigma2.Event.KEY_SERVICE_NAME))
    }

    @Test
    fun fromExtendedHashMapRoundTripsNowAndNext() {
        val now = Event(
            "1", "Now", "100", "60", "100", "d", "x",
            "1:0:1:1:1:1:0:0:0:0:", "TV", "r", "t", "1",
        )
        val next = Event(
            "2", "Next", "160", "60", "100", "d2", "x2",
            "", "", "r2", "t2", "1",
        )
        val row = ServiceNowNext("1:0:1:1:1:1:0:0:0:0:", "TV", now, next)
        val map = serviceNowNextToExtendedHashMap(row)
        val back = serviceNowNextFromExtendedHashMap(map)
        assertEquals(row.serviceReference, back.serviceReference)
        assertEquals(row.serviceName, back.serviceName)
        assertNotNull(back.now)
        assertEquals("Now", back.now!!.title)
        assertEquals("1", back.now!!.eventId)
        assertEquals("100", back.now!!.start)
        assertNotNull(back.next)
        assertEquals("Next", back.next!!.title)
        assertEquals("2", back.next!!.eventId)
        assertEquals("t2", back.next!!.startTimeReadable)
    }

    @Test
    fun emptyTypedListYieldsNoItems() {
        assertEquals(0, serviceListItemsFromNowNext(emptyList()).size)
    }
}
