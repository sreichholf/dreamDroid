package net.reichholf.dreamdroid.tv.ui

import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.multiepg.MultiEpgNowClock
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.tv.activities.MultiEpgActivity
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressHost
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.epg.EpgDetailScreen
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContentOrUnavailable
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors
import net.reichholf.dreamdroid.video.startLiveServiceStream

@Composable
fun TvMultiEpgHost(activity: AppCompatActivity, viewModel: TvMultiEpgViewModel = viewModel()) {
    val context = LocalContext.current
    val session = viewModel.session
    val connection by SessionConnectionHolder.shared.status.collectAsState()
    val prefs = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    val textSize = remember(prefs) {
        MultiEpgTextSize.fromPref(
            prefs.getString(DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE, null)
        )
    }
    val bouquetRef = viewModel.bouquetRef
    val visibleMinutes = viewModel.visibleMinutes
    var nowSec by remember { mutableLongStateOf(MultiEpgNowClock.sec()) }
    var detailEvent by remember { mutableStateOf<Event?>(null) }
    var editTimerEvent by remember { mutableStateOf<Event?>(null) }
    var pickingBouquet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.start(
            extraRef = activity.intent.getStringExtra(MultiEpgActivity.EXTRA_BOUQUET_REF),
            extraName = activity.intent.getStringExtra(MultiEpgActivity.EXTRA_BOUQUET_NAME)
        )
    }

    // Restarting on a bouquet change repaints "now" as the new grid loads.
    LaunchedEffect(bouquetRef) {
        while (isActive) {
            nowSec = MultiEpgNowClock.sec()
            delay(MultiEpgNowClock.TICK_MS)
        }
    }

    LaunchedEffect(session.channels, viewModel.selectedServiceRef, viewModel.selectedStartSec) {
        viewModel.reconcileSelection()
    }

    val zoomSeconds = visibleMinutes * 60L
    val onVisibleWindow = remember(session, zoomSeconds) {
        { start: Long, end: Long -> session.onVisibleWindow(start, end) }
    }

    DreamDroidTvTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            TvMultiEpgScreen(
                bouquetName = viewModel.bouquetName.ifBlank { stringResource(R.string.multiepg) },
                channels = session.channels,
                timelineStartSec = session.timelineStartSec,
                timelineEndSec = session.timelineEndSec,
                nowSec = nowSec,
                originFloorSec = session.originFloorSec,
                loading = session.syncing,
                errorMessage = session.errorMessage,
                selectedServiceRef = viewModel.selectedServiceRef,
                selectedStartSec = viewModel.selectedStartSec,
                onSelectedChange = viewModel::select,
                onJumpToNow = {
                    val now = MultiEpgNowClock.sec()
                    nowSec = now
                    viewModel.jumpToNow(now)
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
                onVisibleMinutesChange = viewModel::onVisibleMinutesChange,
                textSize = textSize,
                timerClocks = session.timerClocks,
                keysEnabled = detailEvent == null && editTimerEvent == null && !pickingBouquet
            )
            val event = detailEvent
            if (event != null) {
                TvMultiEpgEventDetail(
                    event = event,
                    bouquetRef = bouquetRef,
                    activity = activity,
                    progress = viewModel.setTimerProgress,
                    onDismiss = { detailEvent = null },
                    onSetTimer = { viewModel.setTimer(event) },
                    onEditTimer = {
                        detailEvent = null
                        editTimerEvent = event
                    },
                    streamingEnabled = connection.allowsStreaming(),
                    mutationsBlocked = connection.blocksMutations
                )
            }
            if (pickingBouquet) {
                TvMultiEpgBouquetPicker(
                    bouquets = viewModel.bouquets,
                    onPick = { service ->
                        pickingBouquet = false
                        viewModel.pickBouquet(service)
                    },
                    onDismiss = { pickingBouquet = false }
                )
            }
            val editingEvent = editTimerEvent
            if (editingEvent != null) {
                TvTimerEditorHost(
                    timer = Timer.createByEvent(editingEvent),
                    isCreate = true,
                    onDismiss = { editTimerEvent = null },
                    onSaved = {
                        editTimerEvent = null
                        session.load(session.anchorSec, forceRefresh = true, isPull = false)
                    },
                    mutationsBlocked = connection.blocksMutations
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
    progress: IndeterminateProgressState?,
    onDismiss: () -> Unit,
    activity: AppCompatActivity? = null,
    onStream: (() -> Unit)? = null,
    onSetTimer: (() -> Unit)? = null,
    onEditTimer: (() -> Unit)? = null,
    onImdb: (() -> Unit)? = null,
    streamingEnabled: Boolean = true,
    mutationsBlocked: Boolean = false
) {
    val context = LocalContext.current
    val minutesShort = stringResource(R.string.minutes_short)
    val unavailable = stringResource(R.string.not_available)
    val content = event.toEpgDetailContentOrUnavailable(minutesShort, unavailable)
    val firstActionFocus = remember { FocusRequester() }
    var showNeedsReceiver by remember { mutableStateOf(false) }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(event) {
        try {
            firstActionFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // Overlay not attached yet.
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PhoneMaterialTheme.colorScheme.background)
            .focusGroup()
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
                if (streamingEnabled) {
                    TvMultiEpgAction(
                        label = stringResource(R.string.stream),
                        tag = "tv_multi_epg_detail_stream",
                        onClick = {
                            if (onStream != null) {
                                onStream()
                                return@TvMultiEpgAction
                            }
                            val host = activity ?: return@TvMultiEpgAction
                            host.startLiveServiceStream(context, event.serviceReference) {
                                val intent = IntentFactory.getStreamServiceIntent(
                                    context,
                                    event.serviceReference,
                                    event.title,
                                    bouquetRef,
                                    null
                                )
                                TvComposeHubHost.startStreamIntent(host, intent)
                            }
                        },
                        focusRequester = firstActionFocus
                    )
                }
                TvMultiEpgAction(
                    label = stringResource(R.string.set_timer),
                    tag = "tv_multi_epg_detail_set_timer",
                    onClick = {
                        if (mutationsBlocked) {
                            showNeedsReceiver = true
                            return@TvMultiEpgAction
                        }
                        onSetTimer?.invoke()
                    },
                    focusRequester = if (streamingEnabled) {
                        null
                    } else {
                        firstActionFocus
                    }
                )
                TvMultiEpgAction(
                    label = stringResource(R.string.edit_timer),
                    tag = "tv_multi_epg_detail_edit_timer",
                    onClick = {
                        if (mutationsBlocked) {
                            showNeedsReceiver = true
                            return@TvMultiEpgAction
                        }
                        if (onEditTimer != null) {
                            onEditTimer()
                        }
                    }
                )
                TvMultiEpgAction(
                    label = stringResource(R.string.imdb),
                    tag = "tv_multi_epg_detail_imdb",
                    onClick = {
                        if (onImdb != null) {
                            onImdb()
                        } else {
                            val host = activity ?: return@TvMultiEpgAction
                            IntentFactory.queryIMDb(host, event)
                        }
                    }
                )
            }
            IndeterminateProgressHost(progress)
        }
        if (showNeedsReceiver) {
            TvNeedsReceiverOverlay(
                onDismiss = { showNeedsReceiver = false },
                testTag = "tv_multi_epg_needs_receiver"
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMultiEpgAction(
    label: String,
    tag: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
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
    val firstRowFocus = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(bouquets) {
        if (bouquets.isEmpty()) {
            return@LaunchedEffect
        }
        try {
            firstRowFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // Overlay not attached yet.
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PhoneMaterialTheme.colorScheme.background)
            .focusGroup()
            .testTag("tv_multi_epg_bouquet_picker")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bouquets, key = { it.reference }) { service ->
                val isFirst = service.reference == bouquets.first().reference
                Surface(
                    onClick = { onPick(service) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFirst) {
                                Modifier.focusRequester(firstRowFocus)
                            } else {
                                Modifier
                            }
                        )
                        .testTag("tv_multi_epg_bouquet_${service.reference}"),
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

/**
 * Hub launch extras win when the bouquet ref is non-blank, then the profile
 * default TV bouquet, then the first loaded bouquet.
 */
fun resolveTvMultiEpgLaunchBouquet(
    extraRef: String?,
    extraName: String?,
    defaultRef: String,
    defaultName: String,
    firstBouquet: Service?
): Pair<String, String> {
    val launchRef = extraRef?.trim().orEmpty()
    if (launchRef.isNotEmpty()) {
        val launchName = extraName?.trim().orEmpty()
        return launchRef to launchName.ifBlank { launchRef }
    }
    val trimmedDefault = defaultRef.trim()
    if (trimmedDefault.isNotEmpty()) {
        return trimmedDefault to defaultName.ifBlank { trimmedDefault }
    }
    return firstBouquet?.reference.orEmpty() to firstBouquet?.name.orEmpty()
}
