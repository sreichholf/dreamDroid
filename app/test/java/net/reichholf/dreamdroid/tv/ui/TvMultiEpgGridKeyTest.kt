package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.input.key.Key
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgChannelKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvMultiEpgGridKeyTest {
    @Test
    fun upOnFirstChannelDoesNotConsume() {
        val channels = listOf(channel("a"), channel("b"))
        assertFalse(
            consumesTvMultiEpgGridKey(Key.DirectionUp, channels, channels[0].key)
        )
    }

    @Test
    fun upOnLaterChannelConsumes() {
        val channels = listOf(channel("a"), channel("b"))
        assertTrue(
            consumesTvMultiEpgGridKey(Key.DirectionUp, channels, channels[1].key)
        )
    }

    @Test
    fun leftRightCenterStayOnGrid() {
        val channels = listOf(channel("a"))
        assertTrue(consumesTvMultiEpgGridKey(Key.DirectionLeft, channels, channels[0].key))
        assertTrue(consumesTvMultiEpgGridKey(Key.DirectionRight, channels, channels[0].key))
        assertTrue(consumesTvMultiEpgGridKey(Key.DirectionCenter, channels, channels[0].key))
    }

    @Test
    fun upFromSecondOccurrenceOfARepeatedServiceConsumes() {
        val channels = listOf(channel("a"), channel("a", occurrence = 1))
        assertFalse(
            consumesTvMultiEpgGridKey(Key.DirectionUp, channels, channels[0].key)
        )
        assertTrue(
            consumesTvMultiEpgGridKey(Key.DirectionUp, channels, channels[1].key)
        )
    }

    @Test
    fun downMovesToTheSecondOccurrenceOfARepeatedService() {
        val channels = listOf(channel("a"), channel("a", occurrence = 1))
        assertEquals(
            channels[1].key,
            moveDown(channels, channels[0].key)
        )
    }

    @Test
    fun upMovesBackFromTheSecondOccurrence() {
        val channels = listOf(channel("a"), channel("a", occurrence = 1))
        assertEquals(
            channels[0].key,
            moveUp(channels, channels[1].key)
        )
    }

    private fun moveDown(channels: List<MultiEpgChannel>, selectedKey: MultiEpgChannelKey) =
        nextSelection(Key.DirectionDown, channels, selectedKey)

    private fun moveUp(channels: List<MultiEpgChannel>, selectedKey: MultiEpgChannelKey) =
        nextSelection(Key.DirectionUp, channels, selectedKey)

    private fun nextSelection(
        key: Key,
        channels: List<MultiEpgChannel>,
        selectedKey: MultiEpgChannelKey
    ): MultiEpgChannelKey? {
        var next: MultiEpgChannelKey? = null
        handleTvMultiEpgGridKey(
            key = key,
            channels = channels,
            selectedKey = selectedKey,
            selectedStartSec = 1000L,
            originFloorSec = 0L,
            zoomWindowSec = 3600L,
            onSelectedChange = { selected, _ -> next = selected },
            onEventClick = {},
            onVisibleWindow = { _, _ -> },
            onPendingPanRight = {},
            onScrollToChannel = {}
        )
        return next
    }

    private fun channel(ref: String, occurrence: Int = 0) = MultiEpgChannel(
        occurrence = occurrence,
        serviceRef = ref,
        serviceName = ref,
        bars = listOf(
            MultiEpgBar(
                event = Event(eventId = "1", title = "News"),
                startSec = 1000L,
                endSec = 1600L
            )
        )
    )
}
