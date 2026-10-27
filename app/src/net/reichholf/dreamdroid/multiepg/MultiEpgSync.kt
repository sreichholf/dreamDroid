package net.reichholf.dreamdroid.multiepg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.room.EpgChunkMetaEntity
import net.reichholf.dreamdroid.room.EpgDao

/**
 * Windowed `/web/epgmulti` fetch with Room TTL cache and single-flight coalescing.
 * Never omits `endTime` — unbounded bouquet EPG is not allowed. [fetch] gets the window
 * as unix seconds; [net.reichholf.dreamdroid.data.EpgRepository] owns the one instance
 * per process and its receiver fetch.
 */
class MultiEpgSync(
    private val dao: EpgDao,
    private val fetch: suspend (bouquetRef: String, timeSec: Long, endTimeSec: Long) -> List<Event>,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = MultiEpgWindows.DEFAULT_TTL_MS,
    private val farTtlMs: Long = MultiEpgWindows.FAR_TTL_MS,
    private val chunkSeconds: Long = MultiEpgWindows.CHUNK_SECONDS
) {
    private val mutex = Mutex()

    /** Fetch flights for `persist = false` callers; they need the events themselves. */
    private val liveFlights = HashMap<ChunkKey, CompletableDeferred<List<Event>>>()

    /** Persist flights; readers read Room after them, warmers discard the result. */
    private val persistFlights = HashMap<ChunkKey, CompletableDeferred<Unit>>()

    data class ChunkKey(
        val profileId: Int,
        val bouquetRef: String,
        val windowStart: Long
    )

    data class CachedChunk(
        val events: List<Event>,
        val windowStart: Long,
        val windowEnd: Long,
        val fetchedAtMs: Long,
        val fresh: Boolean
    )

    /**
     * Room peek for the bouquet at [unixSec], ignoring TTL.
     * A stored chunk meta hits the UTC window; otherwise events overlapping
     * now for 24 h still paint even when hub fill never wrote `epg_chunk`.
     */
    suspend fun peekChunk(profileId: Int, bouquetRef: String, unixSec: Long): CachedChunk? {
        val chunk = MultiEpgWindows.chunkContaining(unixSec, chunkSeconds)
        val meta = withContext(Dispatchers.IO) {
            dao.getChunk(profileId, bouquetRef, chunk.startSec)
        }
        if (meta != null) {
            val events = withContext(Dispatchers.IO) {
                dao.eventsOverlapping(
                    profileId,
                    bouquetRef,
                    chunk.startSec,
                    chunk.endSec
                ).map { it.toEvent() }
            }
            val nowMs = clockMs()
            val fresh = nowMs - meta.fetchedAtMs <= ttlFor(chunk, nowMs)
            return CachedChunk(events, chunk.startSec, chunk.endSec, meta.fetchedAtMs, fresh)
        }
        val overlapping = withContext(Dispatchers.IO) {
            dao.eventsOverlapping(
                profileId,
                bouquetRef,
                unixSec,
                unixSec + chunkSeconds
            ).map { it.toEvent() }
        }
        if (overlapping.isEmpty()) {
            return null
        }
        return CachedChunk(
            events = overlapping,
            windowStart = chunk.startSec,
            windowEnd = chunk.endSec,
            fetchedAtMs = 0L,
            fresh = false
        )
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
        if (persist) {
            if (!forceRefresh && isChunkFresh(profileId, bouquetRef, chunk)) {
                return readChunk(profileId, bouquetRef, chunk)
            }
            persistChunk(profileId, bouquetRef, chunk)
            return readChunk(profileId, bouquetRef, chunk)
        }
        return fetchWithoutPersisting(profileId, bouquetRef, chunk)
    }

    /**
     * Cache fill for [unixSec]'s chunk. Unlike [ensureChunk] no caller needs the events:
     * a fresh Room chunk is left untouched, and a missing or stale one is fetched and
     * persisted. The grid warms the next chunk this way so panning into it reads Room
     * instead of holding a day of programmes in memory.
     */
    suspend fun warmChunk(profileId: Int, bouquetRef: String, unixSec: Long) {
        val chunk = MultiEpgWindows.chunkContaining(unixSec, chunkSeconds)
        if (isChunkFresh(profileId, bouquetRef, chunk)) {
            return
        }
        persistChunk(profileId, bouquetRef, chunk)
    }

    private suspend fun isChunkFresh(
        profileId: Int,
        bouquetRef: String,
        chunk: MultiEpgWindows.Chunk
    ): Boolean {
        val now = clockMs()
        val meta = withContext(Dispatchers.IO) {
            dao.getChunk(profileId, bouquetRef, chunk.startSec)
        }
        return meta != null && now - meta.fetchedAtMs <= ttlFor(chunk, now)
    }

    private suspend fun readChunk(
        profileId: Int,
        bouquetRef: String,
        chunk: MultiEpgWindows.Chunk
    ): List<Event> = withContext(Dispatchers.IO) {
        dao.eventsOverlapping(
            profileId,
            bouquetRef,
            chunk.startSec,
            chunk.endSec
        ).map { it.toEvent() }
    }

    /**
     * Fetch and persist [chunk] once for every caller that needs it. Readers read Room
     * after the flight; a warm only waits for it and never materialises the chunk.
     */
    private suspend fun persistChunk(
        profileId: Int,
        bouquetRef: String,
        chunk: MultiEpgWindows.Chunk
    ) {
        val key = ChunkKey(profileId, bouquetRef, chunk.startSec)
        var deferred: CompletableDeferred<Unit>
        while (true) {
            var created = false
            mutex.withLock {
                val existing = persistFlights[key]
                if (existing != null) {
                    deferred = existing
                } else {
                    deferred = CompletableDeferred()
                    persistFlights[key] = deferred
                    created = true
                }
            }
            if (created) {
                break
            }
            try {
                deferred.await()
                return
            } catch (_: CancellationException) {
                // The caller that started this fetch was cancelled, not this one: fetch again.
                currentCoroutineContext().ensureActive()
                mutex.withLock {
                    if (persistFlights[key] === deferred) {
                        persistFlights.remove(key)
                    }
                }
            }
        }

        try {
            val events = fetch(bouquetRef, chunk.startSec, chunk.endSec)
            val entities = events.toEpgEventEntities(profileId, bouquetRef)
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
            deferred.complete(Unit)
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            mutex.withLock {
                if (persistFlights[key] === deferred) {
                    persistFlights.remove(key)
                }
            }
        }
    }

    /** Non-persisted containers (Provider / All Services) only need the events. */
    private suspend fun fetchWithoutPersisting(
        profileId: Int,
        bouquetRef: String,
        chunk: MultiEpgWindows.Chunk
    ): List<Event> {
        val key = ChunkKey(profileId, bouquetRef, chunk.startSec)
        var deferred: CompletableDeferred<List<Event>>
        while (true) {
            var created = false
            mutex.withLock {
                val existing = liveFlights[key]
                if (existing != null) {
                    deferred = existing
                } else {
                    deferred = CompletableDeferred()
                    liveFlights[key] = deferred
                    created = true
                }
            }
            if (created) {
                break
            }
            try {
                return deferred.await()
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                mutex.withLock {
                    if (liveFlights[key] === deferred) {
                        liveFlights.remove(key)
                    }
                }
            }
        }

        try {
            val result = fetch(bouquetRef, chunk.startSec, chunk.endSec)
                .toEpgEventEntities(profileId, bouquetRef)
                .map { it.toEvent() }
            deferred.complete(result)
            return result
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            mutex.withLock {
                if (liveFlights[key] === deferred) {
                    liveFlights.remove(key)
                }
            }
        }
    }

    private fun ttlFor(chunk: MultiEpgWindows.Chunk, nowMs: Long): Long = MultiEpgWindows.ttlMs(
        chunkStartSec = chunk.startSec,
        nowSec = nowMs / 1000L,
        nearTtlMs = ttlMs,
        farTtlMs = farTtlMs,
        chunkSeconds = chunkSeconds
    )
}
