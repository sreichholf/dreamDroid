package net.reichholf.dreamdroid.multiepg

import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService
import net.reichholf.dreamdroid.room.EpgEventEntity

/**
 * Offline / stale hub now/next from Room using phone [nowSec], not
 * [EpgEventEntity.currentTime]. Online hub still uses `/web/epgnownext`.
 *
 * Now is the event whose `[start, start+duration)` contains [nowSec]. Next is
 * the later start on that service. Missing overlap leaves Now empty.
 */
fun overlayNowNext(
    rows: List<ServiceNowNext>,
    events: List<EpgEventEntity>,
    nowSec: Long
): List<ServiceNowNext> {
    val byService = events.groupBy { it.serviceRef }
    return rows.map { row ->
        val ref = row.serviceReference
        if (EnigmaService.isDirectory(ref) || EnigmaService.isMarker(ref)) {
            row.copy(now = null, next = null)
        } else {
            val (nowEvent, nextEvent) = nowNextForService(
                byService[ref].orEmpty(),
                nowSec
            )
            row.copy(
                now = nowEvent?.toEvent()?.copy(currentTime = nowSec.toString()),
                next = nextEvent?.toEvent()?.copy(currentTime = nowSec.toString())
            )
        }
    }
}

internal fun nowNextForService(
    events: List<EpgEventEntity>,
    nowSec: Long
): Pair<EpgEventEntity?, EpgEventEntity?> {
    val sorted = events.sortedBy { it.start }
    val nowEvent = sorted.firstOrNull { event ->
        nowSec >= event.start && nowSec < event.start + event.duration
    }
    val nextEvent = if (nowEvent != null) {
        sorted.firstOrNull { it.start > nowEvent.start }
    } else {
        sorted.firstOrNull { it.start >= nowSec }
    }
    return nowEvent to nextEvent
}
