package net.reichholf.dreamdroid.ui.multiepg

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgNowClock
import net.reichholf.dreamdroid.multiepg.MultiEpgRestore
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailHost
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailViewModel
import net.reichholf.dreamdroid.ui.nav.BindShellTopBarActions
import net.reichholf.dreamdroid.ui.nav.DrawerEpgMode
import net.reichholf.dreamdroid.ui.nav.MultiEpg
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTopBarAction

/**
 * MultiEPG destination with stale-while-revalidate sync:
 * paint Room immediately when present, refresh/prefetch in the background,
 * keep stale data on refresh failure, replace on bouquet/profile remount.
 *
 * Loaded grid state lives on [MultiEpgViewModel]. [MultiEpgTopBarSession] supplies
 * the list-EPG top-bar action. The toolbar title is set here.
 */
@Composable
fun MultiEpgDestination(
    handle: PhoneNavHandle,
    route: MultiEpg,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: MultiEpgViewModel = viewModel(),
    detailViewModel: EpgEventDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val session = viewModel.session
    val bouquetRef = MultiEpgRestore.bouquetRef(route.serviceRef)
    val bouquetName = MultiEpgRestore.bouquetName(route.serviceName)
    val focusedServiceRef = route.focusedOrNull()
    var anchorSec by remember(remountEpoch, bouquetRef) {
        val launchSec = route.timeOrNull() ?: System.currentTimeMillis() / 1000L
        mutableLongStateOf(launchSec)
    }
    var visibleStartSec by remember(remountEpoch, bouquetRef) {
        mutableLongStateOf(anchorSec)
    }
    var focusEpoch by remember { mutableIntStateOf(0) }
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var textSize by remember {
        mutableStateOf(
            MultiEpgTextSize.fromPref(
                prefs.getString(DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE, null)
            )
        )
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE) {
                textSize = MultiEpgTextSize.fromPref(prefs.getString(key, null))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val menuSession = remember { MultiEpgTopBarSession() }
    menuSession.handle = handle
    menuSession.context = context
    menuSession.bouquetRef = bouquetRef
    menuSession.bouquetName = bouquetName
    menuSession.visibleStartSec = visibleStartSec

    val defaultTitle = stringResource(R.string.multiepg)
    DisposableEffect(bouquetName) {
        activity.title = bouquetName.ifBlank { defaultTitle }
        onDispose { }
    }

    BindShellTopBarActions(menuSession.topBarActions(stringResource(R.string.epg_list)))

    LaunchedEffect(remountEpoch, bouquetRef) {
        viewModel.ensureLoaded(remountEpoch, bouquetRef, anchorSec)
    }

    var nowSec by remember { mutableLongStateOf(MultiEpgNowClock.sec()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(MultiEpgNowClock.TICK_MS)
            nowSec = MultiEpgNowClock.sec()
        }
    }

    val onVisibleWindow = remember(session) {
        { start: Long, end: Long ->
            visibleStartSec = start
            session.onVisibleWindow(start, end)
        }
    }
    val onEventClick = remember(detailViewModel) {
        { event: Event -> detailViewModel.showDetail(event) }
    }

    MultiEpgScreen(
        bouquetName = bouquetName,
        channels = session.channels,
        timelineStartSec = session.timelineStartSec,
        timelineEndSec = session.timelineEndSec,
        nowSec = nowSec,
        loading = session.syncing,
        pullRefreshing = session.pullRefreshing,
        errorMessage = session.errorMessage,
        focusSec = session.anchorSec,
        focusEpoch = focusEpoch,
        onJumpToNow = {
            val now = MultiEpgNowClock.sec()
            nowSec = now
            anchorSec = now
            session.replaceAndLoad(bouquetRef, now)
            focusEpoch += 1
        },
        onPrevDay = {
            val target = maxOf(
                session.originFloorSec,
                session.anchorSec - MultiEpgWindows.CHUNK_SECONDS
            )
            anchorSec = target
            session.focusAt(target)
            focusEpoch += 1
        },
        onNextDay = {
            val target = session.anchorSec + MultiEpgWindows.CHUNK_SECONDS
            anchorSec = target
            session.focusAt(target)
            focusEpoch += 1
        },
        onRefresh = {
            session.load(session.anchorSec, forceRefresh = true, isPull = true)
        },
        onVisibleWindow = onVisibleWindow,
        onEventClick = onEventClick,
        onAtThisTime = { menuSession.openListEpg() },
        timerClocks = session.timerClocks,
        visibleMinutes = viewModel.visibleMinutes,
        onVisibleMinutesChange = viewModel::onVisibleMinutesChange,
        textSize = textSize,
        focusedServiceRef = focusedServiceRef,
        modifier = modifier
    )
    EpgEventDetailHost(handle, detailViewModel)
}

internal class MultiEpgTopBarSession {
    var handle: PhoneNavHandle? = null
    var context: Context? = null
    var bouquetRef: String = ""
    var bouquetName: String = ""
    var visibleStartSec: Long = 0

    /** The list-EPG jump, once a bouquet is set. */
    fun topBarActions(listEpgLabel: String): List<ShellTopBarAction> = if (bouquetRef.isEmpty()) {
        emptyList()
    } else {
        listOf(
            ShellTopBarAction(
                id = R.id.menu_epg_list,
                label = listEpgLabel,
                iconRes = R.drawable.ic_action_list,
                onClick = { openListEpg() }
            )
        )
    }

    fun openListEpg() {
        val ctx = context ?: return
        DrawerEpgMode.saveList(ctx)
        handle?.navigateToEpg(bouquetRef, bouquetName, timeSec = visibleStartSec)
    }
}
