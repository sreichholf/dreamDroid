package net.reichholf.dreamdroid.ui.multiepg

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.toEnigmaDisplayMessage
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys
import net.reichholf.dreamdroid.multiepg.MultiEpgNowClock
import net.reichholf.dreamdroid.multiepg.MultiEpgPersistGate
import net.reichholf.dreamdroid.multiepg.MultiEpgRestore
import net.reichholf.dreamdroid.multiepg.MultiEpgSession
import net.reichholf.dreamdroid.multiepg.MultiEpgSync
import net.reichholf.dreamdroid.multiepg.MultiEpgSyncHolder
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.TimerSnapshotStore
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.epg.EpgEventDetailSheetHost
import net.reichholf.dreamdroid.ui.epg.EpgEventDialogSession
import net.reichholf.dreamdroid.ui.nav.NavExtras
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * MultiEPG destination with stale-while-revalidate sync:
 * paint Room immediately when present, refresh/prefetch in the background,
 * keep stale data on refresh failure, replace on bouquet/profile remount.
 */
@Composable
fun MultiEpgDestination(
    handle: PhoneNavHandle,
    remountEpoch: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val scope = rememberCoroutineScope()
    val leafArgs = handle.epgLeafArguments()
    val bouquetRef = MultiEpgRestore.bouquetRef(
        leafArgs.getString(EventKeys.KEY_SERVICE_REFERENCE)
    )
    val bouquetName = MultiEpgRestore.bouquetName(
        leafArgs.getString(EventKeys.KEY_SERVICE_NAME)
    )
    val focusedServiceRef = leafArgs.getString(NavExtras.FOCUSED_SERVICE_REF)
    var anchorSec by remember(remountEpoch, bouquetRef) {
        val launchSec = if (leafArgs.containsKey(NavExtras.EPG_TIME_SEC)) {
            leafArgs.getLong(NavExtras.EPG_TIME_SEC)
        } else {
            System.currentTimeMillis() / 1000L
        }
        mutableLongStateOf(launchSec)
    }
    var visibleStartSec by remember(remountEpoch, bouquetRef) {
        mutableLongStateOf(anchorSec)
    }
    var focusEpoch by remember { mutableIntStateOf(0) }
    var visibleMinutes by rememberSaveable {
        mutableIntStateOf(MULTI_EPG_VISIBLE_MINUTES)
    }
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

    val sync = remember(context) { MultiEpgSyncHolder.shared(context) }
    val persistGate = remember(context) {
        MultiEpgPersistGate(UserBouquetCache.excludedHubTabRefs(context))
    }
    val session = remember(sync, scope, context) {
        MultiEpgSession(
            sync = sync,
            scope = scope,
            profileId = { DreamDroid.getCurrentProfile().id ?: -1 },
            noBouquetMessage = context.getString(
                R.string.multiepg_sync_test_no_bouquet
            ),
            fetchTimers = MultiEpgSync.httpFetchTimers(),
            loadBouquetServices = MultiEpgSync.httpFetchBouquet(),
            formatError = { error -> error.toEnigmaDisplayMessage(context) },
            persistBouquet = persistGate::persist,
            shouldSkipReceiverHttp = { hasCache ->
                SessionConnectionHolder.shared.status.value.shouldSkipReceiverHttp(hasCache)
            },
            isSessionOffline = {
                SessionConnectionHolder.shared.status.value.session ==
                    ConnectionStatus.Session.Offline
            },
            loadCachedRoster = { profileId, ref ->
                UserBouquetCache.loadRosterServices(
                    AppDatabase.roster(context),
                    profileId,
                    ref
                )
            },
            loadCachedTimers = { profileId ->
                TimerSnapshotStore.load(AppDatabase.timer(context), profileId)
            }
        )
    }

    val dialogSession = remember { EpgEventDialogSession() }
    dialogSession.handle = handle
    dialogSession.context = context

    DisposableEffect(bouquetName) {
        activity.title = bouquetName.ifBlank { context.getString(R.string.multiepg) }
        onDispose { }
    }

    DisposableEffect(session) {
        onDispose { session.cancel() }
    }

    LaunchedEffect(remountEpoch, bouquetRef) {
        val profileId = DreamDroid.getCurrentProfile().id
        if (profileId != null) {
            persistGate.knownTabRefs = AppDatabase.roster(context).getTabStripRefs(profileId)
        }
        session.replaceAndLoad(bouquetRef, anchorSec)
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
    val onEventClick = remember(dialogSession) {
        { event: Event -> dialogSession.showDetail(event) }
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
        onAtThisTime = {
            handle.navigateToEpg(bouquetRef, bouquetName, timeSec = visibleStartSec)
        },
        timerClocks = session.timerClocks,
        visibleMinutes = visibleMinutes,
        onVisibleMinutesChange = { visibleMinutes = it },
        textSize = textSize,
        focusedServiceRef = focusedServiceRef,
        modifier = modifier
    )
    EpgEventDetailSheetHost(session = dialogSession)
}
