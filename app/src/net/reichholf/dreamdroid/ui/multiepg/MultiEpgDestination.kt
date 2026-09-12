package net.reichholf.dreamdroid.ui.multiepg

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgSync
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.buildMultiEpgChannels
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailSheetHost
import net.reichholf.dreamdroid.ui.epg.EpgEventDialogSession

/**
 * MultiEPG destination with stale-while-revalidate sync:
 * paint Room immediately when present, refresh/prefetch in the background,
 * keep stale data on refresh failure, replace on bouquet/profile remount.
 */
@Composable
fun MultiEpgDestination(
    hostFragment: PhoneNavHostFragment,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val scope = rememberCoroutineScope()
    val leafArgs = hostFragment.epgLeafArguments()

    var bouquetRef by rememberSaveable(remountEpoch) {
        mutableStateOf(leafArgs.getString(EventKeys.KEY_SERVICE_REFERENCE).orEmpty())
    }
    var bouquetName by rememberSaveable(remountEpoch) {
        mutableStateOf(leafArgs.getString(EventKeys.KEY_SERVICE_NAME).orEmpty())
    }
    var anchorSec by rememberSaveable(remountEpoch) {
        mutableLongStateOf(System.currentTimeMillis() / 1000L)
    }
    var channels by remember { mutableStateOf<List<MultiEpgChannel>>(emptyList()) }
    var timelineStartSec by remember { mutableLongStateOf(0L) }
    var timelineEndSec by remember { mutableLongStateOf(0L) }
    var syncingCount by remember { mutableIntStateOf(0) }
    var pullRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var prefetchJob by remember { mutableStateOf<Job?>(null) }
    var warmedEdgeAnchors by remember { mutableStateOf(emptySet<Long>()) }

    val sync = remember(context) {
        MultiEpgSync(
            dao = AppDatabase.epg(context),
            fetch = MultiEpgSync.httpFetch(),
        )
    }

    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.hostFragment = hostFragment
    dialogSession.context = context

    DisposableEffect(bouquetName) {
        activity.title = bouquetName.ifBlank { context.getString(R.string.multiepg) }
        onDispose { }
    }

    fun beginSync() {
        syncingCount += 1
    }

    fun endSync() {
        syncingCount = (syncingCount - 1).coerceAtLeast(0)
    }

    fun prefetchAdjacent(anchor: Long) {
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            beginSync()
            try {
                val profileId = DreamDroid.getCurrentProfile().getId()
                withContext(Dispatchers.IO) {
                    sync.ensureChunk(profileId, ref, anchor - MultiEpgWindows.CHUNK_SECONDS)
                }
                withContext(Dispatchers.IO) {
                    sync.ensureChunk(profileId, ref, anchor + MultiEpgWindows.CHUNK_SECONDS)
                }
            } catch (_: Throwable) {
                // Prefetch failures stay silent; visible-chunk errors are reported separately.
            } finally {
                endSync()
            }
        }
    }

    fun reload(anchor: Long, forceRefresh: Boolean = false, isPull: Boolean = false) {
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) {
            errorMessage = context.getString(R.string.multiepg_sync_test_no_bouquet)
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
                val profileId = DreamDroid.getCurrentProfile().getId()
                if (!forceRefresh) {
                    val peek = withContext(Dispatchers.IO) {
                        sync.peekChunk(profileId, ref, anchor)
                    }
                    if (peek != null && peek.events.isNotEmpty()) {
                        val built = withContext(Dispatchers.Default) {
                            buildMultiEpgChannels(peek.events)
                        }
                        timelineStartSec = peek.windowStart
                        timelineEndSec = peek.windowEnd
                        channels = built
                        anchorSec = anchor
                        if (peek.fresh) {
                            prefetchAdjacent(anchor)
                            return@launch
                        }
                        // Stale: keep painting while ensureChunk refreshes below.
                    }
                }

                val events = withContext(Dispatchers.IO) {
                    sync.ensureChunk(profileId, ref, anchor, forceRefresh = forceRefresh)
                }
                val chunk = MultiEpgWindows.chunkContaining(anchor)
                val built = withContext(Dispatchers.Default) {
                    buildMultiEpgChannels(events)
                }
                timelineStartSec = chunk.startSec
                timelineEndSec = chunk.endSec
                channels = built
                anchorSec = anchor
                prefetchAdjacent(anchor)
            } catch (t: Throwable) {
                // Keep stale grid when rows already exist; always surface a soft error.
                errorMessage = t.message ?: t.javaClass.simpleName
                if (channels.isEmpty()) {
                    // Nothing to keep; leave empty with the error message.
                }
            } finally {
                pullRefreshing = false
                endSync()
            }
        }
    }

    // Bouquet / profile remount: replace immediately.
    LaunchedEffect(remountEpoch, bouquetRef) {
        channels = emptyList()
        timelineStartSec = 0L
        timelineEndSec = 0L
        errorMessage = null
        warmedEdgeAnchors = emptySet()
        reload(anchorSec, forceRefresh = false)
    }

    MultiEpgScreen(
        bouquetName = bouquetName,
        channels = channels,
        timelineStartSec = timelineStartSec,
        timelineEndSec = timelineEndSec,
        nowSec = System.currentTimeMillis() / 1000L,
        loading = syncingCount > 0,
        pullRefreshing = pullRefreshing,
        errorMessage = errorMessage,
        onJumpToNow = {
            reload(System.currentTimeMillis() / 1000L, forceRefresh = false)
        },
        onPrevDay = {
            reload(anchorSec - MultiEpgWindows.CHUNK_SECONDS, forceRefresh = false)
        },
        onNextDay = {
            reload(anchorSec + MultiEpgWindows.CHUNK_SECONDS, forceRefresh = false)
        },
        onRefresh = {
            reload(anchorSec, forceRefresh = true, isPull = true)
        },
        onNearChunkEdge = { towardNext ->
            val edgeAnchor = if (towardNext) {
                anchorSec + MultiEpgWindows.CHUNK_SECONDS
            } else {
                anchorSec - MultiEpgWindows.CHUNK_SECONDS
            }
            if (edgeAnchor !in warmedEdgeAnchors) {
                warmedEdgeAnchors = warmedEdgeAnchors + edgeAnchor
                scope.launch {
                    val ref = bouquetRef.trim()
                    if (ref.isEmpty()) return@launch
                    beginSync()
                    try {
                        val profileId = DreamDroid.getCurrentProfile().getId()
                        withContext(Dispatchers.IO) {
                            sync.ensureChunk(profileId, ref, edgeAnchor)
                        }
                    } catch (_: Throwable) {
                        warmedEdgeAnchors = warmedEdgeAnchors - edgeAnchor
                    } finally {
                        endSync()
                    }
                }
            }
        },
        onEventClick = { dialogSession.showDetail(it) },
        modifier = modifier,
    )
    EpgEventDetailSheetHost(session = dialogSession)
}
