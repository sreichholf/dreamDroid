package net.reichholf.dreamdroid.tv.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.enigma.toEnigmaDisplayMessage
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.multiepg.MultiEpgNowClock
import net.reichholf.dreamdroid.multiepg.MultiEpgPersistGate
import net.reichholf.dreamdroid.multiepg.MultiEpgSession
import net.reichholf.dreamdroid.multiepg.MultiEpgSync
import net.reichholf.dreamdroid.multiepg.MultiEpgSyncHolder
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.MultiEpgZoom
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.epg.EpgDetailScreen
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContentOrUnavailable
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors

@Composable
fun TvMultiEpgHost(activity: AppCompatActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync = remember(context) { MultiEpgSyncHolder.shared(context) }
    val persistGate = remember(context) {
        MultiEpgPersistGate(UserBouquetCache.excludedHubTabRefs(context))
    }
    val session = remember(sync, scope, context) {
        MultiEpgSession(
            sync = sync,
            scope = scope,
            profileId = { DreamDroid.getCurrentProfile().id ?: -1 },
            noBouquetMessage = context.getString(R.string.multiepg_sync_test_no_bouquet),
            fetchTimers = MultiEpgSync.httpFetchTimers(),
            loadBouquetServices = MultiEpgSync.httpFetchBouquet(),
            formatError = { error -> error.toEnigmaDisplayMessage(context) },
            persistBouquet = persistGate::persist,
            shouldSkipReceiverHttp = { false },
            isSessionOffline = { false }
        )
    }
    DisposableEffect(session) {
        onDispose { session.cancel() }
    }

    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    val textSize = remember(prefs) {
        MultiEpgTextSize.fromPref(
            prefs.getString(DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE, null)
        )
    }
    var visibleMinutes by rememberSaveable {
        mutableIntStateOf(MultiEpgZoom.DEFAULT_MINUTES)
    }
    var bouquetRef by remember { mutableStateOf("") }
    var bouquetName by remember { mutableStateOf("") }
    var bouquets by remember { mutableStateOf<List<Service>>(emptyList()) }
    var selectedServiceRef by remember { mutableStateOf("") }
    var selectedStartSec by remember { mutableLongStateOf(0L) }
    var nowSec by remember { mutableLongStateOf(MultiEpgNowClock.sec()) }
    var detailEvent by remember { mutableStateOf<Event?>(null) }
    var pickingBouquet by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<IndeterminateProgressState?>(null) }

    LaunchedEffect(Unit) {
        val excluded = UserBouquetCache.excludedHubTabRefs(context)
        val result = loadServiceList(
            context,
            listOf(NameValuePair("bRef", TvComposeHubHost.BOUQUETS_TV))
        )
        val tabs = UserBouquetCache.userBouquetTabs(result.services, excluded)
        persistGate.knownTabRefs = tabs.map { it.reference }
        bouquets = result.services.filter { it.reference.isNotBlank() }
        val profile = DreamDroid.getCurrentProfile()
        val defaultRef = profile.defaultBouquetTv.orEmpty().trim()
        val defaultName = profile.defaultBouquetTvName.orEmpty()
        if (defaultRef.isNotEmpty()) {
            bouquetRef = defaultRef
            bouquetName = defaultName.ifBlank { defaultRef }
        } else {
            val first = bouquets.firstOrNull()
            bouquetRef = first?.reference.orEmpty()
            bouquetName = first?.name.orEmpty()
        }
        val now = MultiEpgNowClock.sec()
        nowSec = now
        session.replaceAndLoad(bouquetRef, now)
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(MultiEpgNowClock.TICK_MS)
            nowSec = MultiEpgNowClock.sec()
        }
    }

    LaunchedEffect(session.channels, selectedServiceRef, selectedStartSec) {
        val channels = session.channels
        if (channels.isEmpty()) {
            return@LaunchedEffect
        }
        val current = channels.find { it.serviceRef == selectedServiceRef }
        if (current == null) {
            val first = channels.first()
            selectedServiceRef = first.serviceRef
            selectedStartSec = first.bars.firstOrNull()?.startSec ?: 0L
            return@LaunchedEffect
        }
        if (current.bars.isEmpty()) {
            return@LaunchedEffect
        }
        if (current.bars.none { it.startSec == selectedStartSec }) {
            val overlap = current.bars.firstOrNull { bar ->
                bar.startSec <= selectedStartSec && bar.endSec > selectedStartSec
            }
            selectedStartSec = overlap?.startSec ?: current.bars.first().startSec
        }
    }

    val zoomSeconds = visibleMinutes * 60L
    val onVisibleWindow = remember(session, zoomSeconds) {
        { start: Long, end: Long -> session.onVisibleWindow(start, end) }
    }

    DreamDroidTvTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            TvMultiEpgScreen(
                bouquetName = bouquetName.ifBlank { stringResource(R.string.multiepg) },
                channels = session.channels,
                timelineStartSec = session.timelineStartSec,
                timelineEndSec = session.timelineEndSec,
                nowSec = nowSec,
                originFloorSec = session.originFloorSec,
                loading = session.syncing,
                errorMessage = session.errorMessage,
                selectedServiceRef = selectedServiceRef,
                selectedStartSec = selectedStartSec,
                onSelectedChange = { ref, start ->
                    selectedServiceRef = ref
                    selectedStartSec = start
                },
                onJumpToNow = {
                    val now = MultiEpgNowClock.sec()
                    nowSec = now
                    selectedServiceRef = ""
                    selectedStartSec = 0L
                    session.replaceAndLoad(bouquetRef, now)
                },
                onPrevDay = {
                    val target = maxOf(
                        session.originFloorSec,
                        session.anchorSec - MultiEpgWindows.CHUNK_SECONDS
                    )
                    session.focusAt(target)
                    session.onVisibleWindow(target, target + zoomSeconds)
                },
                onNextDay = {
                    val target = session.anchorSec + MultiEpgWindows.CHUNK_SECONDS
                    session.focusAt(target)
                    session.onVisibleWindow(target, target + zoomSeconds)
                },
                onRefresh = {
                    session.load(session.anchorSec, forceRefresh = true, isPull = false)
                },
                onVisibleWindow = onVisibleWindow,
                onEventClick = { event -> detailEvent = event },
                onBouquetClick = { pickingBouquet = true },
                visibleMinutes = visibleMinutes,
                onVisibleMinutesChange = { visibleMinutes = it },
                textSize = textSize,
                timerClocks = session.timerClocks
            )
            val event = detailEvent
            if (event != null) {
                TvMultiEpgEventDetail(
                    event = event,
                    bouquetRef = bouquetRef,
                    activity = activity,
                    progress = progress,
                    onProgress = { progress = it },
                    onDismiss = { detailEvent = null }
                )
            }
            if (pickingBouquet) {
                TvMultiEpgBouquetPicker(
                    bouquets = bouquets,
                    onPick = { service ->
                        pickingBouquet = false
                        if (service.reference != bouquetRef) {
                            bouquetRef = service.reference
                            bouquetName = service.name.ifBlank { service.reference }
                            selectedServiceRef = ""
                            selectedStartSec = 0L
                            val now = MultiEpgNowClock.sec()
                            nowSec = now
                            session.replaceAndLoad(service.reference, now)
                        }
                    },
                    onDismiss = { pickingBouquet = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvMultiEpgEventDetail(
    event: Event,
    bouquetRef: String,
    activity: AppCompatActivity,
    progress: IndeterminateProgressState?,
    onProgress: (IndeterminateProgressState?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val minutesShort = stringResource(R.string.minutes_short)
    val unavailable = stringResource(R.string.not_available)
    val content = event.toEpgDetailContentOrUnavailable(minutesShort, unavailable)
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PhoneMaterialTheme.colorScheme.background)
            .testTag("tv_multi_epg_detail")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EpgDetailScreen(
                content = content,
                onSetTimer = {},
                onEditTimer = {},
                onImdb = {},
                onSimilar = {},
                showActions = false,
                bodyHeightCap = null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvMultiEpgAction(
                    label = stringResource(R.string.stream),
                    tag = "tv_multi_epg_detail_stream",
                    onClick = {
                        val intent = IntentFactory.getStreamServiceIntent(
                            context,
                            event.serviceReference,
                            event.title,
                            bouquetRef,
                            null
                        )
                        TvComposeHubHost.startStreamIntent(activity, intent)
                    }
                )
                TvMultiEpgAction(
                    label = stringResource(R.string.set_timer),
                    tag = "tv_multi_epg_detail_set_timer",
                    onClick = {
                        if (progress != null) {
                            return@TvMultiEpgAction
                        }
                        onProgress(
                            IndeterminateProgressState(
                                message = context.getString(R.string.saving)
                            )
                        )
                        activity.launchSimpleResultLoad(
                            TimerAddByEventIdRequestHandler(),
                            Timer.getEventIdParams(event)
                        ) { _, result, error ->
                            onProgress(null)
                            var toastText = context.getText(R.string.get_content_error)
                                .toString()
                            val stateText = result.stateText
                            when {
                                !stateText.isNullOrEmpty() -> toastText = stateText
                                error != null -> toastText = error.resolve(context).orEmpty()
                            }
                            Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                        }
                    }
                )
                TvMultiEpgAction(
                    label = stringResource(R.string.imdb),
                    tag = "tv_multi_epg_detail_imdb",
                    onClick = { IntentFactory.queryIMDb(activity, event) }
                )
            }
            IndeterminateProgressHost(progress)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMultiEpgAction(label: String, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvMultiEpgBouquetPicker(
    bouquets: List<Service>,
    onPick: (Service) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PhoneMaterialTheme.colorScheme.background)
            .testTag("tv_multi_epg_bouquet_picker")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bouquets, key = { it.reference }) { service ->
                Surface(
                    onClick = { onPick(service) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dreamDroidTvCardColors(),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                ) {
                    Text(
                        text = service.name.ifBlank { service.reference },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}
