package net.reichholf.dreamdroid.multiepg

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.EnigmaClient
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
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
    private val chunkSeconds: Long = MultiEpgWindows.CHUNK_SECONDS,
) {
    private val mutex = Mutex()
    private val inFlight = HashMap<ChunkKey, CompletableDeferred<List<Event>>>()

    data class ChunkKey(
        val profileId: Int,
        val bouquetRef: String,
        val windowStart: Long,
    )

    /**
     * Ensure the 24 h chunk containing [unixSec] is fresh, then return overlapping Room rows
     * for that chunk window (all services).
     */
    suspend fun ensureChunk(
        profileId: Int,
        bouquetRef: String,
        unixSec: Long,
    ): List<Event> {
        val chunk = MultiEpgWindows.chunkContaining(unixSec, chunkSeconds)
        val key = ChunkKey(profileId, bouquetRef, chunk.startSec)
        val now = clockMs()

        val cached = withContext(Dispatchers.IO) {
            dao.getChunk(profileId, bouquetRef, chunk.startSec)
        }
        if (cached != null && now - cached.fetchedAtMs <= ttlMs) {
            return withContext(Dispatchers.IO) {
                dao.eventsOverlapping(profileId, chunk.startSec, chunk.endSec).map { it.toEvent() }
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
            val entities = events.mapNotNull { it.toEpgEventEntity(profileId) }
            val meta = EpgChunkMetaEntity(
                profileId = profileId,
                bouquetRef = bouquetRef,
                windowStart = chunk.startSec,
                windowEnd = chunk.endSec,
                fetchedAtMs = clockMs(),
            )
            withContext(Dispatchers.IO) {
                dao.replaceChunk(meta, entities)
            }
            val result = withContext(Dispatchers.IO) {
                dao.eventsOverlapping(profileId, chunk.startSec, chunk.endSec).map { it.toEvent() }
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
        fun httpFetch(http: SimpleHttpClient = SimpleHttpClient.getInstance()): suspend (String, Long, Long) -> List<Event> {
            return { bouquetRef, timeSec, endTimeSec ->
                require(endTimeSec > timeSec) { "endTime must be after time" }
                val events = EnigmaClient(http).getEvents(
                    listOf(
                        NameValuePair("bRef", bouquetRef),
                        NameValuePair("time", timeSec.toString()),
                        NameValuePair("endTime", endTimeSec.toString()),
                    ),
                    URIStore.EPG_MULTI,
                )
                if (http.hasError()) {
                    error("epgmulti request failed")
                }
                events
            }
        }
    }
}
