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

    @Test
    fun aRunningEventReloadsWhenItEnds() {
        val schedule =
            eventReloadSchedule(event("1"), "ref#1", null, nowMs = (start + 1800) * 1000)
        assertEquals(1_802_000L, schedule.delayMs)
    }

    @Test
    fun anEndedEventRetriesThenPollsSlowly() {
        var schedule: EventReloadSchedule? = null
        val delays =
            (0 until 4).map {
                schedule =
                    eventReloadSchedule(event("1"), "ref#1", schedule, (start + 4000) * 1000)
                schedule!!.delayMs
            }
        assertEquals(listOf(30_000L, 30_000L, 30_000L, 300_000L), delays)
        assertEquals(3, schedule!!.endedEventTries)
    }

    @Test
    fun anotherEndedEventStartsTheTriesOver() {
        val first = eventReloadSchedule(event("1"), "ref#1", null, nowMs = (start + 4000) * 1000)
        val other = eventReloadSchedule(event("2"), "ref#2", first, nowMs = (start + 4000) * 1000)
        assertEquals(30_000L, other.delayMs)
        assertEquals(1, other.endedEventTries)
        assertEquals("ref#2", other.endedEventKey)
    }

    @Test
    fun anotherServiceWithTheSameEventIdStartsTheTriesOver() {
        var schedule: EventReloadSchedule? = null
        (0 until 4).forEach {
            schedule = eventReloadSchedule(event("1"), "ref-a#1", schedule, (start + 4000) * 1000)
        }
        assertEquals(300_000L, schedule!!.delayMs)

        val afterZap =
            eventReloadSchedule(event("1"), "ref-b#1", schedule, nowMs = (start + 4000) * 1000)
        assertEquals(30_000L, afterZap.delayMs)
        assertEquals(1, afterZap.endedEventTries)
        assertEquals("ref-b#1", afterZap.endedEventKey)
    }

    @Test
    fun withoutAnEventItPollsSlowly() {
        val ended = eventReloadSchedule(event("1"), "ref#1", null, nowMs = (start + 4000) * 1000)
        val schedule = eventReloadSchedule(null, null, ended, nowMs = 0)
        assertEquals(300_000L, schedule.delayMs)
        assertEquals(0, schedule.endedEventTries)
    }

    private fun event(eventId: String) =
        Event(eventId = eventId, start = start.toString(), duration = "3600")
}
