package net.reichholf.dreamdroid.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LiveReconnectTest {
    @Test
    fun waitsLongerEachTimeThenGivesUp() {
        val reconnect = LiveReconnect()

        assertEquals(1_000L, reconnect.onFailure())
        assertEquals(2_000L, reconnect.onFailure())
        assertEquals(4_000L, reconnect.onFailure())
        assertNull(reconnect.onFailure())
        assertNull(reconnect.onFailure())
    }

    @Test
    fun aStreamThatPlayedAgainStartsTheCountOver() {
        val reconnect = LiveReconnect()
        reconnect.onFailure()
        reconnect.onFailure()

        reconnect.onPlayed()

        assertEquals(1_000L, reconnect.onFailure())
    }
}
