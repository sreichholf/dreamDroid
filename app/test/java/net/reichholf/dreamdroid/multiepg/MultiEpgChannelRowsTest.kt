package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class MultiEpgChannelRowsTest {
    private val ref = "1:0:1:1:1:1:0:0:0:0:"

    @Test
    fun repeatedServiceKeepsOneRowPerBouquetEntry() {
        val rows = buildMultiEpgChannels(
            events = listOf(event(eventId = "10", start = 1000L)),
            roster = listOf(Service(ref, "Das Erste"), Service(ref, "Das Erste"))
        )

        assertEquals(2, rows.size)
        assertEquals(listOf(0, 1), rows.map { it.occurrence })
        assertEquals(listOf(ref, ref), rows.map { it.serviceRef })
        assertEquals(
            listOf(MultiEpgChannelKey(ref, 0), MultiEpgChannelKey(ref, 1)),
            rows.map { it.key }
        )
        assertSame(rows[0].bars, rows[1].bars)
        assertEquals(1, rows[0].bars.size)
    }

    @Test
    fun rebuildingRepeatedServiceReusesBothRows() {
        val roster = listOf(Service(ref, "Das Erste"), Service(ref, "Das Erste"))
        val events = listOf(event(eventId = "10", start = 1000L))

        val first = buildMultiEpgChannels(events, roster = roster)
        val second = buildMultiEpgChannels(events, previous = first, roster = roster)

        assertSame(first, second)
    }

    @Test
    fun oneOccurrenceDroppedKeepsTheOtherRow() {
        val events = listOf(event(eventId = "10", start = 1000L))
        val both = buildMultiEpgChannels(
            events,
            roster = listOf(Service(ref, "Das Erste"), Service(ref, "Das Erste"))
        )

        val single = buildMultiEpgChannels(
            events,
            previous = both,
            roster = listOf(Service(ref, "Das Erste"))
        )

        assertEquals(listOf(MultiEpgChannelKey(ref, 0)), single.map { it.key })
        assertSame(both[0], single[0])
    }

    private fun event(eventId: String, start: Long) = Event(
        eventId = eventId,
        title = "News",
        start = start.toString(),
        duration = "600",
        serviceReference = ref,
        serviceName = "Das Erste"
    )
}
