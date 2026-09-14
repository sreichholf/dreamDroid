package net.reichholf.dreamdroid.multiepg

import java.util.Calendar
import java.util.TimeZone
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Timer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiEpgTimerClocksTest {
    private val tz: TimeZone = TimeZone.getTimeZone("UTC")
    private val ref = "1:0:1:1:1:1:0:0:0:0:"
    private val start = mondayUtc(20, 0)

    @Test
    fun overlappingRecordTimerMarksBar() {
        val clocks = buildMultiEpgTimerClocks(
            channels = listOf(channel(start)),
            timers = listOf(
                timer(begin = start - 60, end = start + 1800, justPlay = "0")
            ),
            timeZone = tz
        )
        val key = multiEpgTimerClockKey(ref, "10", start)
        assertEquals(MultiEpgTimerClock.Record, clocks[key])
    }

    @Test
    fun justPlayIsZapClock() {
        val clocks = buildMultiEpgTimerClocks(
            channels = listOf(channel(start)),
            timers = listOf(
                timer(begin = start, end = start + 1, justPlay = "1")
            ),
            timeZone = tz
        )
        assertEquals(
            MultiEpgTimerClock.Zap,
            clocks[multiEpgTimerClockKey(ref, "10", start)]
        )
    }

    @Test
    fun disabledAndOtherServiceAreIgnored() {
        val clocks = buildMultiEpgTimerClocks(
            channels = listOf(channel(start)),
            timers = listOf(
                timer(begin = start, end = start + 1800, disabled = "1"),
                timer(
                    reference = "1:0:1:2:1:1:0:0:0:0:",
                    begin = start,
                    end = start + 1800
                )
            ),
            timeZone = tz
        )
        assertTrue(clocks.isEmpty())
    }

    @Test
    fun weeklyTimerMatchesSameWeekdayAndTimeOfDay() {
        val clocks = buildMultiEpgTimerClocks(
            channels = listOf(channel(start)),
            timers = listOf(
                timer(
                    begin = start - 7L * 86400L,
                    end = start - 7L * 86400L + 1800,
                    repeated = "1"
                )
            ),
            timeZone = tz
        )
        assertEquals(
            MultiEpgTimerClock.Record,
            clocks[multiEpgTimerClockKey(ref, "10", start)]
        )
        val tuesday = start + 86400L
        val miss = buildMultiEpgTimerClocks(
            channels = listOf(channel(tuesday, eventId = "11")),
            timers = listOf(
                timer(
                    begin = start - 7L * 86400L,
                    end = start - 7L * 86400L + 1800,
                    repeated = "1"
                )
            ),
            timeZone = tz
        )
        assertNull(miss[multiEpgTimerClockKey(ref, "11", tuesday)])
    }

    @Test
    fun pythonWeekdayMondayIsZero() {
        assertEquals(0, pythonWeekday(start, tz))
        assertEquals(1, pythonWeekday(start + 86400L, tz))
    }

    @Test
    fun serviceKeyDropsBouquetPathAndTrailingColon() {
        assertEquals(
            timerServiceKey("1:0:1:1:1:1:0:0:0:0:"),
            timerServiceKey("1:0:1:1:1:1:0:0:0:0:FROM BOUQUET \"fav\"")
        )
    }

    private fun channel(startSec: Long, eventId: String = "10"): MultiEpgChannel = MultiEpgChannel(
        serviceRef = ref,
        serviceName = "TV",
        bars = listOf(
            MultiEpgBar(
                event = Event(
                    eventId = eventId,
                    title = "News",
                    start = startSec.toString(),
                    duration = "1800",
                    serviceReference = ref,
                    serviceName = "TV"
                ),
                startSec = startSec,
                endSec = startSec + 1800
            )
        )
    )

    private fun timer(
        reference: String = ref,
        begin: Long,
        end: Long,
        justPlay: String = "0",
        disabled: String = "0",
        repeated: String = "0"
    ): Timer = Timer(
        reference = reference,
        begin = begin.toString(),
        end = end.toString(),
        duration = (end - begin).toString(),
        justPlay = justPlay,
        disabled = disabled,
        repeated = repeated
    )

    private fun mondayUtc(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(tz)
        cal.clear()
        cal.set(2024, Calendar.JANUARY, 1, hour, minute, 0)
        return cal.timeInMillis / 1000L
    }
}
