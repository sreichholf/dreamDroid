package net.reichholf.dreamdroid.ui.multiepg

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
 * Phase 2 MultiEPG beachhead: load one 24 h Room/network chunk off the main thread,
 * render a virtualized grid, open the existing EPG detail sheet on tap.
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
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.hostFragment = hostFragment
    dialogSession.context = context

    DisposableEffect(bouquetName) {
        activity.title = bouquetName.ifBlank { context.getString(R.string.multiepg) }
        onDispose { }
    }

    fun reload(anchor: Long) {
        val ref = bouquetRef.trim()
        if (ref.isEmpty()) {
            errorMessage = context.getString(R.string.multiepg_sync_test_no_bouquet)
            channels = emptyList()
            return
        }
        loadJob?.cancel()
        loading = true
        errorMessage = null
        loadJob = scope.launch {
            try {
                val profileId = DreamDroid.getCurrentProfile().getId()
                val sync = MultiEpgSync(
                    dao = AppDatabase.epg(context),
                    fetch = MultiEpgSync.httpFetch(),
                )
                val events = withContext(Dispatchers.IO) {
                    sync.ensureChunk(profileId, ref, anchor)
                }
                val chunk = MultiEpgWindows.chunkContaining(anchor)
                val built = withContext(Dispatchers.Default) {
                    buildMultiEpgChannels(events)
                }
                timelineStartSec = chunk.startSec
                timelineEndSec = chunk.endSec
                channels = built
                anchorSec = anchor
            } catch (t: Throwable) {
                errorMessage = t.message ?: t.javaClass.simpleName
                channels = emptyList()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(remountEpoch, bouquetRef) {
        reload(anchorSec)
    }

    MultiEpgScreen(
        bouquetName = bouquetName,
        channels = channels,
        timelineStartSec = timelineStartSec,
        timelineEndSec = timelineEndSec,
        nowSec = System.currentTimeMillis() / 1000L,
        loading = loading,
        errorMessage = errorMessage,
        onJumpToNow = { reload(System.currentTimeMillis() / 1000L) },
        onEventClick = { dialogSession.showDetail(it) },
        modifier = modifier,
    )
    EpgEventDetailSheetHost(session = dialogSession)
}
