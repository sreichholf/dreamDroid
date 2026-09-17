package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.room.EpgEventEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RoomNowNextTest {
    private val channel = "1:0:1:6DCA:44C:1:C00000:0:0:0:"
    private val folder =
        "1:7:1:0:0:0:0:0:0:0:FROM SATELLITES ORDER BY satellite"
    private val nowSec = 1_000_000L

    @Test
    fun overlappingEventIsNowAndLaterStartIsNext() {
        val nowEvent = event(start = nowSec - 600, duration = 1_200, title = "Now")
        val nextEvent = event(start = nowSec + 600, duration = 1_800, title = "Next")
        val (now, next) = nowNextForService(listOf(nextEvent, nowEvent), nowSec)
        assertEquals("Now", now?.title)
        assertEquals("Next", next?.title)
    }

    @Test
    fun gapLeavesNowEmptyAndUpcomingIsNext() {
        val upcoming = event(start = nowSec + 120, duration = 600, title = "Later")
        val past = event(start = nowSec - 3_600, duration = 1_800, title = "Past")
        val (now, next) = nowNextForService(listOf(past, upcoming), nowSec)
        assertNull(now)
        assertEquals("Later", next?.title)
    }

    @Test
    fun usesPhoneNowNotStoredCurrentTime() {
        val storedBoxTime = nowSec - 10_000
        val overlapping = event(
            start = nowSec - 60,
            duration = 180,
            title = "Live",
            currentTime = storedBoxTime
        )
        val (now, _) = nowNextForService(listOf(overlapping), nowSec)
        assertEquals("Live", now?.title)
        val rows = overlayNowNext(
            listOf(ServiceNowNext(channel, "Das Erste HD")),
            listOf(overlapping),
            nowSec
        )
        assertEquals(nowSec.toString(), rows[0].now?.currentTime)
    }

    @Test
    fun folderRowDoesNotGetNowNext() {
        val overlapping = event(start = nowSec - 60, duration = 180, title = "Live")
        val rows = overlayNowNext(
            listOf(ServiceNowNext(folder, "Satellites")),
            listOf(overlapping.copy(serviceRef = folder)),
            nowSec
        )
        assertNull(rows[0].now)
        assertNull(rows[0].next)
    }

    private fun event(
        start: Long,
        duration: Long,
        title: String,
        currentTime: Long = 0L
    ): EpgEventEntity = EpgEventEntity(
        profileId = 1,
        bouquetRef = "bouquet",
        serviceRef = channel,
        eventId = title,
        start = start,
        duration = duration,
        title = title,
        description = "",
        descriptionExtended = "",
        serviceName = "Das Erste HD",
        currentTime = currentTime
    )
}
