package net.reichholf.dreamdroid.ui.services

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.reichholf.dreamdroid.enigma.EpgNowNextParser
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.testutil.loadWebFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            null
        )
        val directory = ServiceNowNext(
            "1:1:1:0:0:0:0:0:0:0:",
            "Subfolder",
            null,
            null
        )
        val items = serviceListItemsFromNowNext(listOf(marker, directory))
        assertEquals(ServiceRowKind.MARKER, items[0].kind)
        assertEquals(ServiceRowKind.DIRECTORY, items[1].kind)
    }

    @Test
    fun mapsNowAndNextTitlesFromTypedRow() {
        val now = Event(
            eventId = "1",
            title = "Now",
            start = "100",
            duration = "60",
            currentTime = "100",
            description = "d",
            descriptionExtended = "x",
            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
            serviceName = "TV",
            startReadable = "r",
            startTimeReadable = "t",
            durationReadable = "1"
        )
        val next = Event(
            eventId = "2",
            title = "Next",
            start = "160",
            duration = "60",
            currentTime = "100",
            description = "d2",
            descriptionExtended = "x2",
            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
            serviceName = "TV",
            startReadable = "r2",
            startTimeReadable = "t2",
            durationReadable = "1"
        )
        val row = ServiceNowNext("1:0:1:1:1:1:0:0:0:0:", "TV", now, next)
        val items = serviceListItemsFromNowNext(listOf(row))
        assertEquals("TV", items[0].name)
        assertEquals("Now", items[0].nowTitle)
        assertEquals("t", items[0].nowStart)
        assertEquals("Next", items[0].nextTitle)
        assertEquals("t2", items[0].nextStart)
        assertEquals("1", now.eventId)
        assertEquals("2", next.eventId)
    }

    @Test
    fun emptyTypedListYieldsNoItems() {
        assertEquals(0, serviceListItemsFromNowNext(emptyList()).size)
    }
}
