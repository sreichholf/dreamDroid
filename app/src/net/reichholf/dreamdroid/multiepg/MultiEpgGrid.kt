package net.reichholf.dreamdroid.multiepg

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.isUnreachableEnigmaFailure
import net.reichholf.dreamdroid.enigma.toEnigmaDisplayText
import net.reichholf.dreamdroid.ui.text.UiText

/** What the MultiEPG grid paints. [MultiEpgGrid] replaces it as a whole on every change. */
data class MultiEpgGridState(
    val bouquetRef: String = "",
    val channels: List<MultiEpgChannel> = emptyList(),
    val timelineStartSec: Long = 0L,
    val timelineEndSec: Long = 0L,
    val anchorSec: Long = 0L,
    val originFloorSec: Long = 0L,
    val syncingCount: Int = 0,
    val pullRefreshing: Boolean = false,
    val errorMessage: UiText? = null,
    val timerClocks: Map<String, MultiEpgTimerClock> = emptyMap()
) {
    val syncing: Boolean
        get() = syncingCount > 0
}

/**
 * Stale-while-revalidate MultiEPG grid loader shared by the phone and TV ViewModels:
 * peek Room, refresh and prefetch in the background, keep stale rows on error, replace
 * on bouquet/profile remount. Offline skips Enigma HTTP (pull-to-refresh still fetches).
 * Room paints from a stored chunk or from events overlapping now for 24 h, like list EPG.
 *
 * Cache chunks stay 24 h UTC. The painted grid is a sliding window: left edge
 * is the earliest start among programmes overlapping [MultiEpgGridState.originFloorSec]
 * ("now"), the right grows as the viewport moves into the future, and chunks that no
 * longer overlap the padded viewport leave at the front or back. Room still
 * holds them; scrolling back reattaches from cache. Bouquet services loaded
 * from `/web/getservices` keep a row even when a window has no events.
 */
class MultiEpgGrid(
    private val sync: MultiEpgSync,
    private val scope: CoroutineScope,
    private val profileId: () -> Int,
    private val fetchTimers: suspend () -> List<Timer> = { emptyList() },
    private val loadBouquetServices: suspend (String) -> List<Service> = { emptyList() },
    private val persistBouquet: (String) -> Boolean = { true },
    private val shouldSkipReceiverHttp: (Boolean) -> Boolean = { false },
    private val isSessionOffline: () -> Boolean = { false },
    private val loadCachedRoster: suspend (Int, String) -> List<Service>? =
        { _, _ -> null },
    private val loadCachedTimers: suspend (Int) -> List<Timer>? = { null }
) {
    private val _state = MutableStateFlow(MultiEpgGridState())
    val state: StateFlow<MultiEpgGridState> = _state.asStateFlow()

    val loadedWindowStarts: Set<Long>
        get() = eventsByWindow.keys.toSet()

    private val bouquetRef: String
        get() = _state.value.bouquetRef

    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var windowJob: Job? = null
    private val gridMutex = Mutex()
    private val eventsByWindow = ConcurrentHashMap<Long, List<Event>>()
    private var bouquetRoster: List<Service> = emptyList()
    private var visibleStartSec: Long = 0L
    private var visibleEndSec: Long = 0L
    private var timers: List<Timer> = emptyList()

    fun cancel() {
        loadJob?.cancel()
        prefetchJob?.cancel()
        windowJob?.cancel()
    }

    /**
     * Drop the painted grid immediately and load [bouquetRef] at [anchorSec].
     * [anchorSec] becomes the left clamp ("now") for this grid.
     */
    fun replaceAndLoad(bouquetRef: String, anchorSec: Long) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        windowJob?.cancel()
        eventsByWindow.clear()
        bouquetRoster = emptyList()
        timers = emptyList()
        _state.update {
            it.copy(
                bouquetRef = bouquetRef,
                channels = emptyList(),
                timerClocks = emptyMap(),
                timelineStartSec = 0L,
                timelineEndSec = 0L,
                errorMessage = null,
                anchorSec = anchorSec,
                originFloorSec = anchorSec
            )
        }
        visibleStartSec = 0L
        visibleEndSec = 0L
        load(anchorSec, forceRefresh = false, isPull = false)
    }

    fun load(anchorSec: Long, forceRefresh: Boolean = false, isPull: Boolean = false) {
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) {
            timers = emptyList()
            _state.update {
                it.copy(
                    errorMessage = UiText.Resource(R.string.multiepg_sync_test_no_bouquet),
                    channels = emptyList(),
                    timerClocks = emptyMap()
                )
            }
            return
        }
        loadJob?.cancel()
        _state.update {
            it.copy(
                errorMessage = null,
                pullRefreshing = it.pullRefreshing || isPull,
                anchorSec = anchorSec
            )
        }
        loadJob = scope.launch {
            beginSync()
            var timersDeferred: Deferred<List<Timer>>? = null
            try {
                val id = profileId()
                val persist = persistBouquet(ref)
                val peek = if (!forceRefresh) {
                    withContext(Dispatchers.IO) {
                        sync.peekChunk(id, ref, anchorSec)
                    }
                } else {
                    null
                }
                val cachedRoster = withContext(Dispatchers.IO) {
                    loadCachedRoster(id, ref)
                }
                if (cachedRoster != null) {
                    gridMutex.withLock {
                        bouquetRoster = playableMultiEpgRoster(cachedRoster)
                    }
                }
                if (peek != null) {
                    putWindow(peek.windowStart, peek.events)
                } else if (cachedRoster != null) {
                    gridMutex.withLock {
                        publishGridLocked()
                    }
                }
                val hasCache = peek != null || cachedRoster != null
                val skipHttp = isSessionOffline() || shouldSkipReceiverHttp(hasCache)
                if (!forceRefresh && skipHttp) {
                    val cachedTimers = withContext(Dispatchers.IO) {
                        loadCachedTimers(id)
                    }
                    if (cachedTimers != null) {
                        applyTimers(cachedTimers)
                    }
                    return@launch
                }
                timersDeferred = async(Dispatchers.IO) {
                    try {
                        fetchTimers()
                    } catch (t: Throwable) {
                        if (t is CancellationException) {
                            throw t
                        }
                        emptyList()
                    }
                }
                val rosterDeferred = async(Dispatchers.IO) {
                    try {
                        MultiEpgRosterFetch(loadBouquetServices(ref))
                    } catch (t: Throwable) {
                        if (t is CancellationException) {
                            throw t
                        }
                        MultiEpgRosterFetch(error = t)
                    }
                }
                val fetched = rosterDeferred.await()
                gridMutex.withLock {
                    val applied = applyBouquetRoster(bouquetRoster, fetched)
                    bouquetRoster = applied.roster
                    if (applied.errorMessage != null) {
                        _state.update { it.copy(errorMessage = applied.errorMessage) }
                    }
                }
                if (peek != null && peek.events.isNotEmpty()) {
                    if (peek.fresh) {
                        prefetchFuture(anchorSec)
                        return@launch
                    }
                }
                val events = withContext(Dispatchers.IO) {
                    sync.ensureChunk(
                        profileId = id,
                        bouquetRef = ref,
                        unixSec = anchorSec,
                        forceRefresh = forceRefresh,
                        persist = persist
                    )
                }
                val chunk = MultiEpgWindows.chunkContaining(anchorSec)
                putWindow(chunk.startSec, events)
                prefetchFuture(anchorSec)
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    throw t
                }
                surfaceError(t)
            } finally {
                _state.update { it.copy(pullRefreshing = false) }
                endSync()
                val pendingTimers = timersDeferred
                if (isActive && pendingTimers != null) {
                    applyTimers(pendingTimers.await())
                }
            }
        }
    }

    /**
     * Move the sliding window so [unixSec] is in view. Never loads earlier than
     * [MultiEpgGridState.originFloorSec].
     */
    fun focusAt(unixSec: Long) {
        val t = unixSec.coerceAtLeast(_state.value.originFloorSec)
        _state.update { it.copy(anchorSec = t) }
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
            originFloorSec = _state.value.originFloorSec,
            visibleStartSec = visibleStartSec,
            visibleEndSec = visibleEndSec
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
                var snapStart: Long
                var snapEnd: Long
                do {
                    snapStart = this@MultiEpgGrid.visibleStartSec
                    snapEnd = this@MultiEpgGrid.visibleEndSec
                    applySlidingWindow(snapStart, snapEnd)
                } while (
                    snapStart != this@MultiEpgGrid.visibleStartSec ||
                    snapEnd != this@MultiEpgGrid.visibleEndSec
                )
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    throw t
                }
                surfaceError(t)
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
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    throw t
                }
                surfaceError(t)
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
        if (chunk.endSec <= _state.value.originFloorSec) {
            return
        }
        val already = gridMutex.withLock {
            eventsByWindow.containsKey(chunk.startSec)
        }
        if (already) {
            return
        }
        val id = profileId()
        val persist = persistBouquet(ref)
        val peek = withContext(Dispatchers.IO) {
            sync.peekChunk(id, ref, unixSec)
        }
        if (peek != null && peek.events.isNotEmpty()) {
            putWindow(peek.windowStart, peek.events)
            if (peek.fresh) {
                return
            }
        }
        val hasCache = peek != null || eventsByWindow.isNotEmpty()
        if (isSessionOffline() || shouldSkipReceiverHttp(hasCache)) {
            return
        }
        val events = withContext(Dispatchers.IO) {
            sync.ensureChunk(id, ref, unixSec, persist = persist)
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
            originFloorSec = _state.value.originFloorSec,
            visibleStartSec = visibleStartSec,
            visibleEndSec = visibleEndSec
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
            _state.update {
                it.copy(
                    timelineStartSec = 0L,
                    timelineEndSec = 0L,
                    channels = emptyList(),
                    timerClocks = emptyMap()
                )
            }
            return
        }
        val starts = eventsByWindow.keys.sorted()
        val merged = ArrayList<Event>(eventsByWindow.values.sumOf { it.size })
        for (start in starts) {
            merged.addAll(eventsByWindow[start].orEmpty())
        }
        val nextStart = MultiEpgWindows.paintedTimelineStart(
            nowSec = _state.value.originFloorSec,
            minWindowStartSec = starts.first(),
            events = merged
        )
        val nextEnd = starts.last() + MultiEpgWindows.CHUNK_SECONDS
        val previous = _state.value.channels
        val next = withContext(Dispatchers.Default) {
            buildMultiEpgChannels(merged, previous, bouquetRoster)
        }
        val clocks = buildMultiEpgTimerClocks(next, timers)
        _state.update {
            it.copy(
                timelineStartSec = nextStart,
                timelineEndSec = nextEnd,
                channels = next,
                timerClocks = clocks
            )
        }
    }

    private suspend fun applyTimers(list: List<Timer>) {
        gridMutex.withLock {
            timers = list
            val clocks = buildMultiEpgTimerClocks(_state.value.channels, list)
            _state.update { it.copy(timerClocks = clocks) }
        }
    }

    private fun surfaceError(t: Throwable) {
        if (!t.isUnreachableEnigmaFailure()) {
            _state.update { it.copy(errorMessage = t.toEnigmaDisplayText()) }
        }
    }

    private fun beginSync() {
        _state.update { it.copy(syncingCount = it.syncingCount + 1) }
    }

    private fun endSync() {
        _state.update { it.copy(syncingCount = (it.syncingCount - 1).coerceAtLeast(0)) }
    }

    companion object {
        private const val DEFAULT_VISIBLE_SECONDS: Long = 2L * 60L * 60L
    }
}
