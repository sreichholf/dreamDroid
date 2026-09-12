package net.reichholf.dreamdroid.multiepg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.Event

/**
 * Stale-while-revalidate MultiEPG grid session: peek Room, refresh and prefetch
 * in the background, keep stale rows on error, replace on remount.
 */
class MultiEpgSession(
    private val sync: MultiEpgSync,
    private val scope: CoroutineScope,
    private val profileId: () -> Int,
    private val noBouquetMessage: String,
) {
    var bouquetRef: String = ""
        private set
    var channels by mutableStateOf<List<MultiEpgChannel>>(emptyList())
        private set
    var timelineStartSec by mutableLongStateOf(0L)
        private set
    var timelineEndSec by mutableLongStateOf(0L)
        private set
    var anchorSec by mutableLongStateOf(0L)
        private set
    var syncingCount by mutableIntStateOf(0)
        private set
    var pullRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val syncing: Boolean
        get() = syncingCount > 0

    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var warmedEdgeAnchors: Set<Long> = emptySet()

    fun cancel() {
        loadJob?.cancel()
        prefetchJob?.cancel()
    }

    /**
     * Drop the painted grid immediately and load [bouquetRef] at [anchorSec].
     */
    fun replaceAndLoad(bouquetRef: String, anchorSec: Long) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        channels = emptyList()
        timelineStartSec = 0L
        timelineEndSec = 0L
        errorMessage = null
        warmedEdgeAnchors = emptySet()
        this.bouquetRef = bouquetRef
        this.anchorSec = anchorSec
        load(anchorSec, forceRefresh = false, isPull = false)
    }

    fun load(
        anchorSec: Long,
        forceRefresh: Boolean = false,
        isPull: Boolean = false,
    ) {
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) {
            errorMessage = noBouquetMessage
            channels = emptyList()
            return
        }
        loadJob?.cancel()
        errorMessage = null
        if (isPull) {
            pullRefreshing = true
        }
        loadJob = scope.launch {
            beginSync()
            try {
                val id = profileId()
                if (!forceRefresh) {
                    val peek = withContext(Dispatchers.IO) {
                        sync.peekChunk(id, ref, anchorSec)
                    }
                    if (peek != null && peek.events.isNotEmpty()) {
                        applyChunk(
                            events = peek.events,
                            windowStart = peek.windowStart,
                            windowEnd = peek.windowEnd,
                            anchorSec = anchorSec,
                        )
                        if (peek.fresh) {
                            prefetchAdjacent(anchorSec)
                            return@launch
                        }
                    }
                }
                val events = withContext(Dispatchers.IO) {
                    sync.ensureChunk(
                        profileId = id,
                        bouquetRef = ref,
                        unixSec = anchorSec,
                        forceRefresh = forceRefresh,
                    )
                }
                val chunk = MultiEpgWindows.chunkContaining(anchorSec)
                applyChunk(
                    events = events,
                    windowStart = chunk.startSec,
                    windowEnd = chunk.endSec,
                    anchorSec = anchorSec,
                )
                prefetchAdjacent(anchorSec)
            } catch (t: Throwable) {
                errorMessage = t.message ?: t.javaClass.simpleName
            } finally {
                pullRefreshing = false
                endSync()
            }
        }
    }

    fun onNearChunkEdge(towardNext: Boolean) {
        val edgeAnchor = if (towardNext) {
            anchorSec + MultiEpgWindows.CHUNK_SECONDS
        } else {
            anchorSec - MultiEpgWindows.CHUNK_SECONDS
        }
        if (edgeAnchor in warmedEdgeAnchors) {
            return
        }
        warmedEdgeAnchors = warmedEdgeAnchors + edgeAnchor
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) {
            return
        }
        scope.launch {
            beginSync()
            try {
                withContext(Dispatchers.IO) {
                    sync.ensureChunk(profileId(), ref, edgeAnchor)
                }
            } catch (_: Throwable) {
                warmedEdgeAnchors = warmedEdgeAnchors - edgeAnchor
            } finally {
                endSync()
            }
        }
    }

    suspend fun awaitIdle() {
        loadJob?.join()
        prefetchJob?.join()
    }

    private suspend fun applyChunk(
        events: List<Event>,
        windowStart: Long,
        windowEnd: Long,
        anchorSec: Long,
    ) {
        val built = withContext(Dispatchers.Default) {
            buildMultiEpgChannels(events)
        }
        timelineStartSec = windowStart
        timelineEndSec = windowEnd
        channels = built
        this.anchorSec = anchorSec
    }

    private fun prefetchAdjacent(anchor: Long) {
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) {
            return
        }
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            beginSync()
            try {
                val id = profileId()
                withContext(Dispatchers.IO) {
                    sync.ensureChunk(
                        id,
                        ref,
                        anchor - MultiEpgWindows.CHUNK_SECONDS,
                    )
                }
                withContext(Dispatchers.IO) {
                    sync.ensureChunk(
                        id,
                        ref,
                        anchor + MultiEpgWindows.CHUNK_SECONDS,
                    )
                }
            } catch (_: Throwable) {
                // Prefetch failures stay silent.
            } finally {
                endSync()
            }
        }
    }

    private fun beginSync() {
        syncingCount += 1
    }

    private fun endSync() {
        syncingCount = (syncingCount - 1).coerceAtLeast(0)
    }
}
