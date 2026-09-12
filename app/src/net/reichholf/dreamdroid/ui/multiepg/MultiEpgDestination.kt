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
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys
import net.reichholf.dreamdroid.multiepg.MultiEpgSession
import net.reichholf.dreamdroid.multiepg.MultiEpgSync
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
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

    val sync = remember(context) {
        MultiEpgSync(
            dao = AppDatabase.epg(context),
            fetch = MultiEpgSync.httpFetch(),
        )
    }
    val session = remember(sync, scope, context) {
        MultiEpgSession(
            sync = sync,
            scope = scope,
            profileId = { DreamDroid.getCurrentProfile().getId() },
            noBouquetMessage = context.getString(
                R.string.multiepg_sync_test_no_bouquet,
            ),
        )
    }

    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.hostFragment = hostFragment
    dialogSession.context = context

    DisposableEffect(bouquetName) {
        activity.title = bouquetName.ifBlank { context.getString(R.string.multiepg) }
        onDispose { }
    }

    DisposableEffect(session) {
        onDispose { session.cancel() }
    }

    LaunchedEffect(remountEpoch, bouquetRef) {
        session.replaceAndLoad(bouquetRef, anchorSec)
    }

    MultiEpgScreen(
        bouquetName = bouquetName,
        channels = session.channels,
        timelineStartSec = session.timelineStartSec,
        timelineEndSec = session.timelineEndSec,
        nowSec = System.currentTimeMillis() / 1000L,
        loading = session.syncing,
        pullRefreshing = session.pullRefreshing,
        errorMessage = session.errorMessage,
        onJumpToNow = {
            val now = System.currentTimeMillis() / 1000L
            anchorSec = now
            session.load(now, forceRefresh = false)
        },
        onPrevDay = {
            session.load(
                session.anchorSec - MultiEpgWindows.CHUNK_SECONDS,
                forceRefresh = false,
            )
        },
        onNextDay = {
            session.load(
                session.anchorSec + MultiEpgWindows.CHUNK_SECONDS,
                forceRefresh = false,
            )
        },
        onRefresh = {
            session.load(session.anchorSec, forceRefresh = true, isPull = true)
        },
        onNearChunkEdge = { towardNext ->
            session.onNearChunkEdge(towardNext)
        },
        onEventClick = { dialogSession.showDetail(it) },
        modifier = modifier,
    )
    EpgEventDetailSheetHost(session = dialogSession)
}
