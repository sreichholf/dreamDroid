package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.reichholf.dreamdroid.enigma.EnigmaClient
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.valueOrThrow
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.multiepg.MultiEpgPersistGate
import net.reichholf.dreamdroid.multiepg.MultiEpgSync
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.nowNextForService
import net.reichholf.dreamdroid.multiepg.toEvent
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgEventEntity
import net.reichholf.dreamdroid.room.EpgSearchRecentEntity
import net.reichholf.dreamdroid.room.epgSearchKey
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/** One step of a list EPG load. */
sealed interface EventListLoad {
    /** Events to show. [cached] ones come from the MultiEPG Room chunks. */
    data class Events(val events: List<Event>, val cached: Boolean) : EventListLoad

    /** The receiver failed and Room has nothing to show instead. */
    data class Failed(val error: EnigmaHttpError?) : EventListLoad
}

/**
 * EPG of the active profile. MultiEPG (`/web/epgmulti`) fills Room chunks through
 * [multiEpgSync]. List EPG (`/web/epgbouquet`, `/web/epgservice`) reads those chunks as its
 * offline cache and never writes them; see docs/offline-and-errors.md and docs/multiepg.md.
 */
@Singleton
class EpgRepository @Inject constructor(
    private val clients: EnigmaClientFactory,
    private val profiles: ProfileRepository,
    private val database: AppDatabase,
    private val sessions: SessionConnectionHolder,
    private val services: ServiceRepository
) {
    private val epgMultiRequest = Mutex()
    private val epgSearchRequest = Mutex()

    /**
     * The process's one MultiEPG chunk cache. MultiEPG, the hub service list, and the TV hub
     * share it, so they coalesce `/web/epgmulti` work. Built on first use: Hilt constructs
     * this repository before the pre-Room import runs.
     */
    val multiEpgSync: MultiEpgSync by lazy {
        MultiEpgSync(dao = database.epgDao(), fetch = ::fetchEpgMulti)
    }

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

    /**
     * Cached programmes of the active profile whose title contains [query], ignoring case,
     * that have not ended yet. Room only holds the MultiEPG chunks of bouquets opened on
     * this device, so this is a subset of [receiverSearch]. Null when Room holds no EPG for
     * the profile at all.
     */
    suspend fun cachedSearch(query: String): List<Event>? {
        val profileId = profiles.requireCurrent().id ?: return null
        val dao = database.epgDao()
        if (!dao.hasEvents(profileId)) {
            return null
        }
        val nowSec = System.currentTimeMillis() / 1000L
        return dao.searchTitles(profileId, epgSearchKey(query), nowSec, SEARCH_LIMIT)
            .map { it.toEvent() }
    }

    /**
     * Whether a receiver search should be skipped: the session is Offline and Room has EPG
     * for the active profile. Same rule as [listLoad].
     */
    suspend fun skipsReceiverSearch(): Boolean {
        val status = sessions.status.value
        if (!status.shouldSkipReceiverHttp(hasCache = true)) {
            return false
        }
        val profileId = profiles.requireCurrent().id ?: return false
        return status.shouldSkipReceiverHttp(database.epgDao().hasEvents(profileId))
    }

    /**
     * Receiver-side EPG search by title (`/web/epgsearch`). Searches run one at a time for
     * the whole app: the box scans its whole EPG per search, and a cancelled caller's HTTP
     * call still finishes, so the next search waits for it.
     */
    suspend fun receiverSearch(query: String): EnigmaResponse<List<Event>> =
        epgSearchRequest.withLock {
            clients.current().getEvents(
                listOf(NameValuePair("search", query)),
                URIStore.EPG_SEARCH
            )
        }

    /**
     * The programme of [serviceRef] that starts at [beginSec]: from the MultiEPG cache when a
     * cached bouquet holds it, else from the receiver unless the session is Offline. Null
     * when neither has it, as when the EPG changed, or the receiver did not answer.
     */
    suspend fun event(serviceRef: String, beginSec: Long): Event? {
        profiles.requireCurrent().id?.let { profileId ->
            database.epgDao().eventAt(profileId, serviceRef, beginSec)?.let { return it.toEvent() }
        }
        if (sessions.status.value.shouldSkipReceiverHttp(hasCache = true)) {
            return null
        }
        // The box answers `time` plus one minute with the programme running then.
        val events = clients.current().getEvents(
            listOf(
                NameValuePair("sRef", serviceRef),
                NameValuePair("time", beginSec.toString()),
                NameValuePair("endTime", "1")
            ),
            URIStore.EPG_SERVICE
        ).value
        return events?.firstOrNull { it.start == beginSec.toString() }
    }

    /** The newest recent EPG searches first. */
    fun recentSearches(): Flow<List<String>> =
        database.epgDao().recentSearches(RECENT_SEARCHES).map { rows -> rows.map { it.query } }

    /** Stores [query] as the newest recent search. */
    suspend fun rememberSearch(query: String, nowMs: Long = System.currentTimeMillis()) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return
        }
        database.epgDao().addRecentSearch(
            EpgSearchRecentEntity(epgSearchKey(trimmed), trimmed, nowMs),
            keep = RECENT_SEARCHES
        )
    }

    suspend fun forgetSearch(query: String) {
        database.epgDao().deleteRecentSearch(epgSearchKey(query.trim()))
    }

    /**
     * A MultiEPG persist gate that knows no user bouquet yet, so it persists nothing. Callers
     * set its known tabs from [ServiceRepository].
     */
    fun multiEpgPersistGate(): MultiEpgPersistGate = MultiEpgPersistGate(services.excludedTabRefs)

    /**
     * Use-driven EPG fill: `/web/epgmulti` for the chunk at [nowSec] into Room when the user
     * opened [containerRef] under the hub tab [tabRootRef] and that container is cacheable
     * (docs/offline-and-errors.md). Provider, All Services, and the bouquet index never
     * fetch. Failures throw.
     */
    suspend fun fillNowChunk(
        containerRef: String,
        tabRootRef: String,
        nowSec: Long = System.currentTimeMillis() / 1000L
    ): List<Event> {
        val profileId = profiles.requireCurrent().id ?: return emptyList()
        if (!services.isCacheableContainer(containerRef, tabRootRef)) {
            return emptyList()
        }
        return multiEpgSync.ensureChunk(profileId, containerRef, nowSec, persist = true)
    }

    /** Drops MultiEPG events and chunks that ended two days before [nowSec] or earlier. */
    suspend fun pruneExpiredMultiEpgCache(nowSec: Long = System.currentTimeMillis() / 1000L) {
        database.epgDao().pruneOlderThan(MultiEpgWindows.retentionCutoffSec(nowSec))
    }

    /**
     * One `/web/epgmulti` window. `time` is the unix start; `endTime` is the **duration in
     * minutes** (eEPGCache's 4th tuple arg, as GraphMultiEPG passes it) despite its name. An
     * absolute unix end overflows on the box and yields no events. Requests run one at a
     * time, so the box never serves two bouquet dumps at once.
     */
    private suspend fun fetchEpgMulti(
        bouquetRef: String,
        timeSec: Long,
        endTimeSec: Long
    ): List<Event> {
        require(endTimeSec > timeSec) { "window end must be after start" }
        val durationMinutes = ((endTimeSec - timeSec) / 60L).coerceAtLeast(1L)
        return epgMultiRequest.withLock {
            clients.current().getEvents(
                listOf(
                    NameValuePair("bRef", bouquetRef),
                    NameValuePair("time", timeSec.toString()),
                    NameValuePair("endTime", durationMinutes.toString())
                ),
                URIStore.EPG_MULTI
            ).valueOrThrow()
        }
    }

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

    private companion object {
        /** Upper bound of one cached search result list. */
        const val SEARCH_LIMIT = 256

        /** How many recent searches are kept. */
        const val RECENT_SEARCHES = 10
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
