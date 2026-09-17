package net.reichholf.dreamdroid.multiepg

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.EnigmaClient
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.valueOrThrow
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.EpgDao

/**
 * Windowed `/web/epgmulti` fetch with Room TTL cache and single-flight coalescing.
 * Never omits `endTime` — unbounded bouquet EPG is not allowed.
 */
class MultiEpgSync(
    private val dao: EpgDao,
    private val fetch: suspend (bouquetRef: String, timeSec: Long, endTimeSec: Long) -> List<Event>,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = MultiEpgWindows.DEFAULT_TTL_MS,
    private val chunkSeconds: Long = MultiEpgWindows.CHUNK_SECONDS
) {
    private val mutex = Mutex()
    private val inFlight = HashMap<ChunkKey, CompletableDeferred<List<Event>>>()

    data class ChunkKey(
        val profileId: Int,
        val bouquetRef: String,
        val windowStart: Long,
        val persist: Boolean
    )

    data class CachedChunk(
        val events: List<Event>,
        val windowStart: Long,
        val windowEnd: Long,
        val fetchedAtMs: Long,
        val fresh: Boolean
    )

    /**
     * Room peek for the chunk containing [unixSec], ignoring TTL.
     * Used to paint immediately while a background refresh runs.
     */
    suspend fun peekChunk(profileId: Int, bouquetRef: String, unixSec: Long): CachedChunk? {
        val chunk = MultiEpgWindows.chunkContaining(unixSec, chunkSeconds)
        val meta = withContext(Dispatchers.IO) {
            dao.getChunk(profileId, bouquetRef, chunk.startSec)
        } ?: return null
        val events = withContext(Dispatchers.IO) {
            dao.eventsOverlapping(
                profileId,
                bouquetRef,
                chunk.startSec,
                chunk.endSec
            ).map { it.toEvent() }
        }
        val fresh = clockMs() - meta.fetchedAtMs <= ttlMs
        return CachedChunk(events, chunk.startSec, chunk.endSec, meta.fetchedAtMs, fresh)
    }

    /**
     * Ensure the 24 h chunk containing [unixSec] is available.
     * When [forceRefresh] is false, a fresh TTL hit returns Room rows without hitting the box.
     * When [forceRefresh] is true (pull-to-refresh), always refetch (still single-flight).
     * When [persist] is false, still fetch for display but do not write Room
     * (Provider / All Services / aggregate index).
     */
    suspend fun ensureChunk(
        profileId: Int,
        bouquetRef: String,
        unixSec: Long,
        forceRefresh: Boolean = false,
        persist: Boolean = true
    ): List<Event> {
        val chunk = MultiEpgWindows.chunkContaining(unixSec, chunkSeconds)
        val key = ChunkKey(profileId, bouquetRef, chunk.startSec, persist)
        val now = clockMs()

        if (persist && !forceRefresh) {
            val cached = withContext(Dispatchers.IO) {
                dao.getChunk(profileId, bouquetRef, chunk.startSec)
            }
            if (cached != null && now - cached.fetchedAtMs <= ttlMs) {
                return withContext(Dispatchers.IO) {
                    dao.eventsOverlapping(
                        profileId,
                        bouquetRef,
                        chunk.startSec,
                        chunk.endSec
                    ).map { it.toEvent() }
                }
            }
        }

        val deferred: CompletableDeferred<List<Event>>
        var created = false
        mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                deferred = existing
            } else {
                deferred = CompletableDeferred()
                inFlight[key] = deferred
                created = true
            }
        }

        if (!created) {
            return deferred.await()
        }

        try {
            val events = fetch(bouquetRef, chunk.startSec, chunk.endSec)
            val entities = events.toEpgEventEntities(profileId, bouquetRef)
            if (!persist) {
                val result = entities.map { it.toEvent() }
                deferred.complete(result)
                return result
            }
            val meta = EpgChunkMetaEntity(
                profileId = profileId,
                bouquetRef = bouquetRef,
                windowStart = chunk.startSec,
                windowEnd = chunk.endSec,
                fetchedAtMs = clockMs()
            )
            withContext(Dispatchers.IO) {
                dao.replaceChunk(meta, entities)
                dao.pruneOlderThan(
                    MultiEpgWindows.retentionCutoffSec(clockMs() / 1000L)
                )
            }
            val result = withContext(Dispatchers.IO) {
                dao.eventsOverlapping(
                    profileId,
                    bouquetRef,
                    chunk.startSec,
                    chunk.endSec
                ).map { it.toEvent() }
            }
            deferred.complete(result)
            return result
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            mutex.withLock {
                if (inFlight[key] === deferred) {
                    inFlight.remove(key)
                }
            }
        }
    }

    companion object {
        /**
         * Dreambox `/web/epgmulti` query params:
         * - `time` = unix start
         * - `endTime` = **duration in minutes** (eEPGCache 4th tuple arg), despite the name —
         *   GraphMultiEPG passes `time_epoch` minutes the same way. Absolute unix end is wrong
         *   and yields empty results (overflow in startTimeQuery).
         */
        fun httpFetch(
            http: EnigmaHttp = EnigmaHttp()
        ): suspend (String, Long, Long) -> List<Event> = { bouquetRef, timeSec, endTimeSec ->
            require(endTimeSec > timeSec) { "window end must be after start" }
            val durationMinutes = ((endTimeSec - timeSec) / 60L).coerceAtLeast(1L)
            EnigmaClient(http).getEvents(
                listOf(
                    NameValuePair("bRef", bouquetRef),
                    NameValuePair("time", timeSec.toString()),
                    NameValuePair("endTime", durationMinutes.toString())
                ),
                URIStore.EPG_MULTI
            ).valueOrThrow()
        }

        fun httpFetchTimers(http: EnigmaHttp = EnigmaHttp()): suspend () -> List<Timer> = {
            EnigmaClient(http).getTimers().value ?: emptyList()
        }

        /** Bouquet members from `/web/getservices?sRef=`. HTTP failures throw. */
        fun httpFetchBouquet(http: EnigmaHttp = EnigmaHttp()): suspend (String) -> List<Service> =
            { bouquetRef ->
                EnigmaClient(http).getServices(
                    listOf(NameValuePair("sRef", bouquetRef))
                ).valueOrThrow()
            }
    }
}
