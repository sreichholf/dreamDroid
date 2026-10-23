package net.reichholf.dreamdroid.ui.video

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.Python
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventProgressTest {
    private val start = 1_700_000_000L

    @Test
    fun runningEventFollowsTheDeviceClockNotTheFetchTime() {
        val event = Event(
            start = start.toString(),
            duration = "3600",
            currentTime = (start + 60).toString()
        )
        val progress = eventProgress(event, nowMs = (start + 1800) * 1000)
        assertEquals(EventProgress(startSec = start, elapsedSec = 1800, totalSec = 3600), progress)
        assertEquals(30, progress!!.leftMinutes)
    }

    @Test
    fun clampsBeforeStartAndAfterEnd() {
        val event = Event(start = start.toString(), duration = "3600")
        assertEquals(0, eventProgress(event, (start - 100) * 1000)!!.elapsedSec)
        assertEquals(3600, eventProgress(event, (start + 5000) * 1000)!!.elapsedSec)
        assertEquals(0, eventProgress(event, (start + 5000) * 1000)!!.leftMinutes)
    }

    @Test
    fun partialMinutesLeftRoundUp() {
        val event = Event(start = start.toString(), duration = "3600")
        assertEquals(1, eventProgress(event, (start + 3590) * 1000)!!.leftMinutes)
    }

    @Test
    fun noProgressWithoutUsableTimes() {
        assertNull(eventProgress(null, 0))
        assertNull(eventProgress(Event(start = "", duration = "3600"), 0))
        assertNull(eventProgress(Event(start = start.toString(), duration = Python.NONE), 0))
        assertNull(eventProgress(Event(start = start.toString(), duration = "0"), 0))
        assertNull(eventProgress(Event(start = "x", duration = "3600"), 0))
    }
}
