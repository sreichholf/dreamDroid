package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.input.key.Key
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TvMultiEpgGridKeyTest {
    @Test
    fun upOnFirstChannelDoesNotConsume() {
        val channels = listOf(channel("a"), channel("b"))
        assertFalse(
            consumesTvMultiEpgGridKey(Key.DirectionUp, channels, "a")
        )
    }

    @Test
    fun upOnLaterChannelConsumes() {
        val channels = listOf(channel("a"), channel("b"))
        assertTrue(
            consumesTvMultiEpgGridKey(Key.DirectionUp, channels, "b")
        )
    }

    @Test
    fun leftRightCenterStayOnGrid() {
        val channels = listOf(channel("a"))
        assertTrue(consumesTvMultiEpgGridKey(Key.DirectionLeft, channels, "a"))
        assertTrue(consumesTvMultiEpgGridKey(Key.DirectionRight, channels, "a"))
        assertTrue(consumesTvMultiEpgGridKey(Key.DirectionCenter, channels, "a"))
    }

    private fun channel(ref: String) = MultiEpgChannel(
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
