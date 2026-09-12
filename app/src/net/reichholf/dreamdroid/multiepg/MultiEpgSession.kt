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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.Event
import java.util.concurrent.ConcurrentHashMap

/**
 * Stale-while-revalidate MultiEPG grid session: peek Room, refresh and prefetch
 * in the background, keep stale rows on error, replace on bouquet/profile remount.
 *
 * Cache chunks stay 24 h UTC. The painted grid is a sliding window: left edge
 * is the earliest start among programmes overlapping [originFloorSec] ("now"),
 * the right grows as the viewport moves into the future, and chunks that no
 * longer overlap the padded viewport leave at the front or back. Room still
 * holds them; scrolling back reattaches from cache.
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
    var originFloorSec by mutableLongStateOf(0L)
        private set
    var syncingCount by mutableIntStateOf(0)
        private set
    var pullRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val syncing: Boolean
        get() = syncingCount > 0

    val loadedWindowStarts: Set<Long>
        get() = eventsByWindow.keys.toSet()

    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var windowJob: Job? = null
    private val gridMutex = Mutex()
    private val eventsByWindow = ConcurrentHashMap<Long, List<Event>>()
    private var visibleStartSec: Long = 0L
    private var visibleEndSec: Long = 0L

    fun cancel() {
        loadJob?.cancel()
        prefetchJob?.cancel()
        windowJob?.cancel()
    }

    /**
     * Drop the painted grid immediately and load [bouquetRef] at [anchorSec].
     * [anchorSec] becomes the left clamp ("now") for this session.
     */
    fun replaceAndLoad(bouquetRef: String, anchorSec: Long) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        windowJob?.cancel()
        eventsByWindow.clear()
        channels = emptyList()
        timelineStartSec = 0L
        timelineEndSec = 0L
        errorMessage = null
        this.bouquetRef = bouquetRef
        this.anchorSec = anchorSec
        this.originFloorSec = anchorSec
        visibleStartSec = 0L
        visibleEndSec = 0L
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
        this.anchorSec = anchorSec
        loadJob = scope.launch {
            beginSync()
            try {
                val id = profileId()
                if (!forceRefresh) {
                    val peek = withContext(Dispatchers.IO) {
                        sync.peekChunk(id, ref, anchorSec)
                    }
                    if (peek != null && peek.events.isNotEmpty()) {
                        putWindow(peek.windowStart, peek.events)
                        if (peek.fresh) {
                            prefetchFuture(anchorSec)
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
                putWindow(chunk.startSec, events)
                prefetchFuture(anchorSec)
            } catch (t: Throwable) {
                errorMessage = t.message ?: t.javaClass.simpleName
            } finally {
                pullRefreshing = false
                endSync()
            }
        }
    }

    /**
     * Move the sliding window so [unixSec] is in view. Never loads earlier than
     * [originFloorSec].
     */
    fun focusAt(unixSec: Long) {
        val t = unixSec.coerceAtLeast(originFloorSec)
        anchorSec = t
        onVisibleWindow(t, t + DEFAULT_VISIBLE_SECONDS)
    }

    /**
     * Align painted chunks with the viewport. Adds missing chunks at either end
     * and drops chunks that no longer overlap the padded range. Calling this
     * while panning inside the same chunks is a no-op.
     */
    fun onVisibleWindow(visibleStartSec: Long, visibleEndSec: Long) {
        if (bouquetRef.trim().isEmpty() || eventsByWindow.isEmpty()) {
            return
        }
        this.visibleStartSec = visibleStartSec
        this.visibleEndSec = visibleEndSec
        val want = MultiEpgWindows.slidingChunks(
            originFloorSec = originFloorSec,
            visibleStartSec = visibleStartSec,
            visibleEndSec = visibleEndSec,
        )
        if (want.toSet() == eventsByWindow.keys.toSet() && windowJob?.isActive != true) {
            return
        }
        if (windowJob?.isActive == true) {
            return
        }
        beginSync()
        windowJob = scope.launch {
            try {
                var snapStart = this@MultiEpgSession.visibleStartSec
                var snapEnd = this@MultiEpgSession.visibleEndSec
                do {
                    snapStart = this@MultiEpgSession.visibleStartSec
                    snapEnd = this@MultiEpgSession.visibleEndSec
                    applySlidingWindow(snapStart, snapEnd)
                } while (
                    snapStart != this@MultiEpgSession.visibleStartSec ||
                    snapEnd != this@MultiEpgSession.visibleEndSec
                )
            } catch (_: Throwable) {
                // Window sync failures stay silent; painted range stays put.
            } finally {
                endSync()
            }
        }
    }

    suspend fun awaitIdle() {
        loadJob?.join()
        prefetchJob?.join()
        windowJob?.join()
        prefetchJob?.join()
        windowJob?.join()
    }

    private fun prefetchFuture(anchor: Long) {
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) {
            return
        }
        prefetchJob?.cancel()
        beginSync()
        prefetchJob = scope.launch {
            try {
                attachWindow(anchor + MultiEpgWindows.CHUNK_SECONDS)
            } catch (_: Throwable) {
                // Prefetch failures stay silent.
            } finally {
                endSync()
            }
        }
    }

    private suspend fun attachWindow(unixSec: Long) {
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) {
            return
        }
        val chunk = MultiEpgWindows.chunkContaining(unixSec)
        if (chunk.endSec <= originFloorSec) {
            return
        }
        val already = gridMutex.withLock {
            eventsByWindow.containsKey(chunk.startSec)
        }
        if (already) {
            return
        }
        val id = profileId()
        val peek = withContext(Dispatchers.IO) {
            sync.peekChunk(id, ref, unixSec)
        }
        if (peek != null && peek.events.isNotEmpty()) {
            putWindow(peek.windowStart, peek.events)
            if (peek.fresh) {
                return
            }
        }
        val events = withContext(Dispatchers.IO) {
            sync.ensureChunk(id, ref, unixSec)
        }
        putWindow(chunk.startSec, events)
    }

    private suspend fun putWindow(windowStart: Long, events: List<Event>) {
        gridMutex.withLock {
            eventsByWindow[windowStart] = events
            publishGridLocked()
        }
    }

    private suspend fun applySlidingWindow(visibleStartSec: Long, visibleEndSec: Long) {
        val want = MultiEpgWindows.slidingChunks(
            originFloorSec = originFloorSec,
            visibleStartSec = visibleStartSec,
            visibleEndSec = visibleEndSec,
        )
        if (want.isEmpty()) {
            return
        }
        val wantSet = want.toSet()
        val same = gridMutex.withLock { wantSet == eventsByWindow.keys.toSet() }
        if (same) {
            return
        }
        for (start in want) {
            attachWindow(start)
        }
        gridMutex.withLock {
            val extras = eventsByWindow.keys.filter { it !in wantSet }
            if (extras.isNotEmpty()) {
                for (start in extras) {
                    eventsByWindow.remove(start)
                }
                publishGridLocked()
            }
        }
    }

    private suspend fun publishGridLocked() {
        if (eventsByWindow.isEmpty()) {
            timelineStartSec = 0L
            timelineEndSec = 0L
            channels = emptyList()
            return
        }
        val starts = eventsByWindow.keys.sorted()
        val merged = ArrayList<Event>(eventsByWindow.values.sumOf { it.size })
        for (start in starts) {
            merged.addAll(eventsByWindow[start].orEmpty())
        }
        timelineStartSec = MultiEpgWindows.paintedTimelineStart(
            nowSec = originFloorSec,
            minWindowStartSec = starts.first(),
            events = merged,
        )
        timelineEndSec = starts.last() + MultiEpgWindows.CHUNK_SECONDS
        val previous = channels
        val next = withContext(Dispatchers.Default) {
            buildMultiEpgChannels(merged, previous)
        }
        if (next !== previous) {
            channels = next
        }
    }

    private fun beginSync() {
        syncingCount += 1
    }

    private fun endSync() {
        syncingCount = (syncingCount - 1).coerceAtLeast(0)
    }

    companion object {
        private const val DEFAULT_VISIBLE_SECONDS: Long = 2L * 60L * 60L
    }
}
