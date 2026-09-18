package net.reichholf.dreamdroid.ui.epg

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.toEvent
import net.reichholf.dreamdroid.room.EpgDao

/**
 * List EPG (bouquet `/web/epgbouquet` and per-service `/web/epgservice`) reads
 * shared MultiEPG Room chunks. Do not persist `epgbouquet` separately.
 */
object ListEpgCache {
    /**
     * Events for [bouquetRef] from [fromSec] for one 24 h window, or null if
     * that container was never written.
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
            return events.map { it.toEvent() }
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
