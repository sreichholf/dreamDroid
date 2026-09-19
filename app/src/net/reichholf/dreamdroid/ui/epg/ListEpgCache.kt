package net.reichholf.dreamdroid.ui.epg

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.nowNextForService
import net.reichholf.dreamdroid.multiepg.toEvent
import net.reichholf.dreamdroid.room.EpgDao
import net.reichholf.dreamdroid.room.EpgEventEntity

/**
 * List EPG (bouquet `/web/epgbouquet` and per-service `/web/epgservice`) reads
 * shared MultiEPG Room chunks. Do not persist `epgbouquet` separately.
 */
object ListEpgCache {
    /**
     * One programme per channel at [fromSec], matching `/web/epgbouquet?time=`.
     * Null if that container was never written.
     */
    suspend fun loadBouquetEvents(
        dao: EpgDao,
        profileId: Int,
        bouquetRef: String,
        fromSec: Long
    ): List<Event>? {
        val windowEnd = fromSec + MultiEpgWindows.CHUNK_SECONDS
        val events = dao.eventsOverlapping(profileId, bouquetRef, fromSec, windowEnd)
        if (events.isNotEmpty()) {
            return bouquetEventsAtInstant(events, fromSec)
        }
        val chunk = MultiEpgWindows.chunkContaining(fromSec)
        if (dao.getChunk(profileId, bouquetRef, chunk.startSec) != null) {
            return emptyList()
        }
        return null
    }

    /**
     * Events for [serviceRef] starting at or after [fromSec], or null if that
     * service was never written to Room.
     */
    suspend fun loadServiceEvents(
        dao: EpgDao,
        profileId: Int,
        serviceRef: String,
        fromSec: Long
    ): List<Event>? {
        if (dao.eventCountForService(profileId, serviceRef) == 0) {
            return null
        }
        return dao.eventsForServiceFrom(profileId, serviceRef, fromSec).map { it.toEvent() }
    }
}

/**
 * `/web/epgbouquet` is one row per channel: the event whose
 * `[start, start+duration)` contains [atSec]. Bouquet order is kept.
 */
fun bouquetEventsAtInstant(events: List<EpgEventEntity>, atSec: Long): List<Event> {
    val order = LinkedHashSet<String>()
    val byService = HashMap<String, ArrayList<EpgEventEntity>>()
    for (event in events) {
        order.add(event.serviceRef)
        byService.getOrPut(event.serviceRef) { ArrayList() }.add(event)
    }
    val out = ArrayList<Event>(order.size)
    for (ref in order) {
        val nowEvent = nowNextForService(byService.getValue(ref), atSec).first ?: continue
        out.add(nowEvent.toEvent())
    }
    return out
}
