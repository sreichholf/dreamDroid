package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.reichholf.dreamdroid.enigma.EnigmaClient
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.nowNextForService
import net.reichholf.dreamdroid.multiepg.toEvent
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgEventEntity
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/** One step of a list EPG load. */
sealed interface EventListLoad {
    /** Events to show. [cached] ones come from the MultiEPG Room chunks. */
    data class Events(val events: List<Event>, val cached: Boolean) : EventListLoad

    /** The receiver failed and Room has nothing to show instead. */
    data class Failed(val error: EnigmaHttpError?) : EventListLoad
}

/**
 * EPG of the active profile. List EPG (`/web/epgbouquet`, `/web/epgservice`) reads the
 * MultiEPG Room chunks as its offline cache and never writes them; see
 * docs/offline-and-errors.md and docs/multiepg.md.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val clients: EnigmaClientFactory,
    private val profiles: ProfileRepository,
    private val database: AppDatabase,
    private val sessions: SessionConnectionHolder
) {
    /**
     * One programme per channel of [bouquetRef] at [atSec]. See [listLoad] for the
     * cache and receiver order.
     */
    fun bouquetEvents(
        bouquetRef: String,
        atSec: Long,
        forceRefresh: Boolean = false
    ): Flow<EventListLoad> = listLoad(
        forceRefresh = forceRefresh,
        readCache = { profileId -> cachedBouquetEvents(profileId, bouquetRef, atSec) },
        fetch = { client ->
            client.getEvents(
                listOf(NameValuePair("bRef", bouquetRef), NameValuePair("time", atSec.toString())),
                URIStore.EPG_BOUQUET
            )
        }
    )

    /** The schedule of [serviceRef] from now on. See [listLoad]. */
    fun serviceEvents(serviceRef: String, forceRefresh: Boolean = false): Flow<EventListLoad> =
        listLoad(
            forceRefresh = forceRefresh,
            readCache = { profileId ->
                cachedServiceEvents(profileId, serviceRef, System.currentTimeMillis() / 1000L)
            },
            fetch = { client ->
                client.getEvents(listOf(NameValuePair("sRef", serviceRef)), URIStore.EPG_SERVICE)
            }
        )

    /** Receiver-side EPG search by title. Not cached. */
    suspend fun search(query: String): EnigmaResponse<List<Event>> =
        clients.current().getEvents(listOf(NameValuePair("search", query)), URIStore.EPG_SEARCH)

    /**
     * Unless [forceRefresh], paints Room first and skips the receiver while the session is
     * Offline and Room had the list. Then asks the receiver; when that fails, Room is the
     * fallback (also on a forced refresh) before the failure is reported.
     */
    private fun listLoad(
        forceRefresh: Boolean,
        readCache: suspend (profileId: Int) -> List<Event>?,
        fetch: suspend (EnigmaClient) -> EnigmaResponse<List<Event>>
    ): Flow<EventListLoad> = flow {
        val profileId = profiles.requireCurrent().id
        suspend fun cached(): List<Event>? = profileId?.let { readCache(it) }

        val painted = if (forceRefresh) null else cached()
        if (painted != null) {
            emit(EventListLoad.Events(painted, cached = true))
        }
        if (!forceRefresh && sessions.status.value.shouldSkipReceiverHttp(painted != null)) {
            return@flow
        }
        val response = fetch(clients.current())
        val live = response.value
        if (live != null) {
            emit(EventListLoad.Events(live, cached = false))
            return@flow
        }
        val fallback = cached()
        if (fallback != null) {
            emit(EventListLoad.Events(fallback, cached = true))
        } else {
            emit(EventListLoad.Failed(response.error))
        }
    }

    /**
     * One programme per channel at [fromSec], matching `/web/epgbouquet?time=`. Null when
     * that container's chunk was never written.
     */
    private suspend fun cachedBouquetEvents(
        profileId: Int,
        bouquetRef: String,
        fromSec: Long
    ): List<Event>? {
        val dao = database.epgDao()
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

    /** Events of [serviceRef] from [fromSec] on, or null when Room never had that service. */
    private suspend fun cachedServiceEvents(
        profileId: Int,
        serviceRef: String,
        fromSec: Long
    ): List<Event>? {
        val dao = database.epgDao()
        if (dao.eventCountForService(profileId, serviceRef) == 0) {
            return null
        }
        return dao.eventsForServiceFrom(profileId, serviceRef, fromSec).map { it.toEvent() }
    }
}

/**
 * `/web/epgbouquet` is one row per channel: the event whose `[start, start+duration)`
 * contains [atSec]. Bouquet order is kept.
 */
internal fun bouquetEventsAtInstant(events: List<EpgEventEntity>, atSec: Long): List<Event> {
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
