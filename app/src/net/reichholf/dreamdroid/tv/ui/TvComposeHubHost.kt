package net.reichholf.dreamdroid.tv.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LastLiveStream
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.tv.view.FittedEllipsisText
import net.reichholf.dreamdroid.tv.view.ImageCardContent
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.services.nowProgress
import net.reichholf.dreamdroid.ui.text.asString
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvDrawerItemColors

/**
 * Phase 3.1c-iv Compose TV hub host.
 * - **iv-b..e:** Compose hub beachhead through movie rows.
 * - **iv-f:** Compose hub is the TV default; Leanback browse path removed.
 *   Stream intents honor the integrated/external player pref. Overlay zap list
 *   is Compose TV cards (`androidx.leanback` removed).
 */
object TvComposeHubHost {
    fun defaultSettingsKinds(): List<BrowseItem.Kind> = listOf(
        BrowseItem.Kind.Reload,
        BrowseItem.Kind.Preferences,
        BrowseItem.Kind.Profile
    )

    fun settingsTitleRes(kind: BrowseItem.Kind): Int = when (kind) {
        BrowseItem.Kind.Reload -> R.string.reload
        BrowseItem.Kind.Preferences -> R.string.settings
        BrowseItem.Kind.Profile -> R.string.profile
    }

    /** Same badges the Leanback Live TV settings cards used. */
    fun settingsBadgeRes(kind: BrowseItem.Kind): Int = when (kind) {
        BrowseItem.Kind.Reload -> R.drawable.ic_badge_reload
        BrowseItem.Kind.Preferences -> R.drawable.ic_badge_settings
        BrowseItem.Kind.Profile -> R.drawable.ic_badge_profiles
    }

    /** Plays [stream] of [service], titled by its now event, as a channel of [bouquetRef]. */
    fun streamServiceIntent(
        context: Context,
        service: ServiceNowNext,
        bouquetRef: String?,
        stream: LiveStream.Ready
    ): Intent {
        val title = service.now?.title?.takeIf { it.isNotEmpty() } ?: service.serviceName
        return IntentFactory.getStreamServiceIntent(context, stream, title, bouquetRef, service)
    }

    /** Plays the recording [movie] from [url] (see `MovieRepository.streamUrl`). */
    fun streamMovieIntent(context: Context, movie: Movie, url: String): Intent =
        IntentFactory.getStreamFileIntent(context, url, movie.title, movie)

    /** The player intent for [stream]. */
    fun streamIntent(context: Context, stream: TvStreamOpen): Intent = when (stream) {
        is TvStreamOpen.Service ->
            streamServiceIntent(context, stream.service, stream.bouquetRef, stream.stream)

        is TvStreamOpen.Recording -> streamMovieIntent(context, stream.movie, stream.url)
    }

    /** Starts [intent]; calls [onMissingPlayer] when no app on the device can play it. */
    fun startStreamIntent(activity: Activity, intent: Intent, onMissingPlayer: () -> Unit) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            onMissingPlayer()
        }
    }

    fun install(activity: ComponentActivity) {
        activity.setContent {
            TvHubNavHost(activity = activity)
        }
    }
}

data class HubBouquetRow(val bouquet: Service, val services: List<ServiceNowNext>)

/** 5% focus scale needs inset so the first grid row is not clipped by the title. */
private val HubGridItemSpacing = 24.dp
private val HubGridFocusInset = 16.dp

/** One compact card size for the hub grid and the zap overlay. */
internal val HubServiceCardHeight = 120.dp
private val HubServiceProgressHeight = 8.dp
private val HubServicePiconWidth = 80.dp
private val HubServicePiconHeight = 48.dp

/** Zap overlay row card: wide enough for text next to the leading picon. */
private val HubServiceRowCardWidth = 300.dp

/** The smallest D-pad / touch target of the top bar and the empty state. */
private val HubMinTouchTarget = 48.dp

@Composable
fun ComposeTvHubApp(
    activity: ComponentActivity,
    gate: TvSessionGate?,
    onRecheckProfile: () -> Unit,
    viewModel: TvHubViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenProfiles: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    LifecycleStartEffect(viewModel) {
        viewModel.onShown()
        onStopOrDispose { viewModel.onHidden() }
    }
    val stream = uiState.stream
    LaunchedEffect(stream) {
        if (stream != null) {
            TvComposeHubHost.startStreamIntent(
                activity,
                TvComposeHubHost.streamIntent(activity, stream),
                viewModel::onMissingStreamPlayer
            )
            viewModel.onStreamStarted()
        }
    }
    // Room answers before the gate is known, so Checking never flashes ProfileCheck.
    if (gate == null) {
        return
    }
    val status = uiState.connection
    val settingsItems = TvComposeHubHost.defaultSettingsKinds().map { kind ->
        kind to stringResource(TvComposeHubHost.settingsTitleRes(kind))
    }

    if (gate is TvSessionGate.Checking || gate is TvSessionGate.Failed) {
        DreamDroidTvTheme {
            TvProfileCheckScreen(
                gate = gate,
                onRecheck = onRecheckProfile,
                onProfiles = onOpenProfiles
            )
        }
        return
    }

    val streamingEnabled = uiState.streamingEnabled
    Box(modifier = Modifier.fillMaxSize()) {
        ComposeTvHubChrome(
            section = uiState.section,
            onOpenTab = viewModel::openTab,
            onOpenSettings = viewModel::openSettings,
            onDrawerItemSelected = viewModel::selectDrawerItem,
            onBack = { viewModel.back() },
            settingsItems = settingsItems,
            onSettingsClick = { kind ->
                when (kind) {
                    BrowseItem.Kind.Reload -> viewModel.reload()
                    BrowseItem.Kind.Preferences -> onOpenSettings()
                    BrowseItem.Kind.Profile -> onOpenProfiles()
                }
            },
            bouquetRows = uiState.bouquetRows,
            movieLocations = uiState.movieLocations,
            moviesByLocation = uiState.moviesByLocation,
            loading = uiState.loading,
            movieLoading = uiState.movieLoading,
            errorText = uiState.browseError?.asString(),
            streamingEnabled = streamingEnabled,
            onServiceClick = viewModel::streamService,
            onMovieClick = viewModel::streamMovie,
            lastStream = uiState.lastStream,
            profileName = uiState.profileName,
            sessionChipLabel = stringResource(status.chipLabelRes()),
            onOpenProfiles = onOpenProfiles,
            onReload = viewModel::reload,
            onSessionRecheck = if (shouldShowTvSessionRecheck(status)) {
                onRecheckProfile
            } else {
                null
            },
            onServiceInfo = viewModel::showServiceTimer,
            guideContent = { bouquetRef, bouquetName ->
                TvMultiEpgHost(
                    activity = activity,
                    bouquetRef = bouquetRef,
                    bouquetName = bouquetName,
                    onBouquetChange = viewModel::selectDrawerItem
                )
            }
        )
        val overlayTarget = uiState.serviceTimerTarget
        val editorEvent = uiState.editTimerEvent
        // Drop the INFO overlay while the editor is open so D-pad reaches the form
        // (same as MultiEPG dismissing detail before TvTimerEditorHost).
        if (overlayTarget != null && editorEvent == null) {
            DreamDroidTvTheme {
                TvServiceTimerOverlay(
                    service = overlayTarget.service,
                    onDismiss = viewModel::dismissServiceTimer,
                    onStream = {
                        viewModel.streamService(overlayTarget.service, overlayTarget.bouquetRef)
                        viewModel.dismissServiceTimer()
                    },
                    onSetTimer = viewModel::setTimer,
                    onEditTimer = viewModel::showEditTimer,
                    streamingEnabled = streamingEnabled,
                    mutationsBlocked = status.blocksMutations
                )
            }
        }
        if (editorEvent != null) {
            DreamDroidTvTheme {
                TvTimerEditorHost(
                    timer = Timer.createByEvent(editorEvent),
                    isCreate = true,
                    onDismiss = viewModel::dismissEditTimer,
                    onSaved = viewModel::onTimerSaved
                )
            }
        }
    }
}

/**
 * The hub for [section]: the top bar tabs (Live TV, Guide, Recordings, Timers) with the
 * profile + connection chip and the Settings icon, then the section's drawer, if it has one,
 * beside its content. Back from any section but Live TV calls [onBack].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ComposeTvHubChrome(
    section: HubSection,
    onOpenTab: (HubTab) -> Unit,
    onOpenSettings: () -> Unit,
    onDrawerItemSelected: (String) -> Unit,
    settingsItems: List<Pair<BrowseItem.Kind, String>>,
    onSettingsClick: (BrowseItem.Kind) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    bouquetRows: List<HubBouquetRow> = emptyList(),
    movieLocations: List<String> = emptyList(),
    moviesByLocation: Map<String, List<Movie>> = emptyMap(),
    loading: Boolean = false,
    movieLoading: Boolean = false,
    errorText: String? = null,
    streamingEnabled: Boolean = true,
    onServiceClick: (ServiceNowNext, String?) -> Unit = { _, _ -> },
    onMovieClick: (Movie) -> Unit = {},
    lastStream: LastLiveStream? = null,
    profileName: String = "",
    sessionChipLabel: String? = null,
    onOpenProfiles: () -> Unit = {},
    onReload: () -> Unit = {},
    onSessionRecheck: (() -> Unit)? = null,
    sessionRecheckLabel: String? = null,
    onServiceInfo: ((ServiceNowNext, String?) -> Unit)? = null,
    timerContent: @Composable () -> Unit = {
        TvTimerHost(modifier = Modifier.fillMaxSize())
    },
    guideContent: @Composable (bouquetRef: String, bouquetName: String) -> Unit =
        { bouquetRef, bouquetName ->
            TvMultiEpgHost(
                activity = LocalActivity.current as ComponentActivity,
                bouquetRef = bouquetRef,
                bouquetName = bouquetName,
                onBouquetChange = onDrawerItemSelected
            )
        }
) {
    var showStreamUnavailable by remember { mutableStateOf(false) }
    val tabs = hubTabs(bouquetRows, movieLocations)
    val unnamedBouquet = stringResource(R.string.services)
    val drawerItems = section.drawerItems(bouquetRows, movieLocations, unnamedBouquet)
    val selectedBouquet = (section as? HubSection.Live)?.bouquetRef?.let { ref ->
        bouquetRows.firstOrNull { it.bouquet.reference == ref }
    }
    val movieDir = (section as? HubSection.Recordings)?.location
    // Live TV's content starts with "Continue watching" above the grid, when a channel was
    // saved for the active profile.
    val continueWatching = lastStream.takeIf { selectedBouquet != null }
    // On start the hub focuses Live TV's first card (Continue watching, else the first
    // service) so the remote acts on content instead of an unclear cursor. One-shot per hub
    // entry, and never once the user has moved through the drawer, so late-loading rows do
    // not pull focus out of it.
    val serviceGridFocus = remember { FocusRequester() }
    val continueWatchingFocus = remember { FocusRequester() }
    val gridFocus = serviceGridFocus.takeIf { selectedBouquet?.services?.isNotEmpty() == true }
    val liveContentFocus = if (continueWatching != null) continueWatchingFocus else gridFocus
    val liveTabFocus = remember { FocusRequester() }
    var initialGridFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(selectedBouquet?.bouquet?.reference, liveContentFocus) {
        if (!initialGridFocusDone && liveContentFocus != null) {
            // The grid composes its cards during layout; wait for that frame.
            withFrameNanos {}
            initialGridFocusDone = liveContentFocus.tryRequestFocus()
        }
    }
    // Back lands on Live TV's grid (its last focused card), else on the Live TV tab.
    var focusLiveAfterBack by remember { mutableStateOf(false) }
    BackHandler(enabled = section !is HubSection.Live) {
        focusLiveAfterBack = true
        onBack()
    }
    LaunchedEffect(focusLiveAfterBack, section) {
        if (focusLiveAfterBack && section is HubSection.Live) {
            focusLiveAfterBack = false
            withFrameNanos {}
            if (!serviceGridFocus.tryRequestFocus()) {
                liveTabFocus.tryRequestFocus()
            }
        }
    }
    val liveEmpty = section is HubSection.Live && drawerItems.isNullOrEmpty() && !loading
    val hasPaintedContent = selectedBouquet?.services?.isNotEmpty() == true ||
        (movieDir != null && moviesByLocation[movieDir].orEmpty().isNotEmpty())
    val showError = !liveEmpty &&
        shouldShowTvBrowseError(section, loading, errorText, hasPaintedContent)
    val gatedServiceClick: (ServiceNowNext, String?) -> Unit = { service, bouquetRef ->
        when (tvHubServiceRowKind(service.serviceReference)) {
            TvHubServiceRowKind.MARKER_HEADER -> Unit

            TvHubServiceRowKind.CHANNEL,
            TvHubServiceRowKind.SPACER -> {
                if (!streamingEnabled) {
                    showStreamUnavailable = true
                } else {
                    onServiceClick(service, bouquetRef)
                }
            }
        }
    }
    val gatedMovieClick: (Movie) -> Unit = { movie ->
        if (!streamingEnabled) {
            showStreamUnavailable = true
        } else {
            onMovieClick(movie)
        }
    }
    DreamDroidTvTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .testTag("compose_tv_hub_chrome")
            ) {
                HubTopBar(
                    tabs = tabs,
                    section = section,
                    onOpenTab = onOpenTab,
                    onOpenSettings = onOpenSettings,
                    liveTabFocus = liveTabFocus,
                    profileName = profileName,
                    sessionChipLabel = sessionChipLabel,
                    onOpenProfiles = onOpenProfiles,
                    onSessionRecheck = onSessionRecheck,
                    recheckLabel = sessionRecheckLabel ?: stringResource(R.string.recheck)
                )
                val pane: @Composable () -> Unit = {
                    HubSectionPane(
                        section = section,
                        title = section.title(bouquetRows)?.asString(),
                        loading = loading,
                        errorText = errorText.takeIf { showError },
                        content = {
                            when (section) {
                                is HubSection.Live -> if (liveEmpty) {
                                    HubLiveEmptyState(
                                        message = errorText
                                            ?: stringResource(R.string.tv_hub_no_bouquets),
                                        onReload = onReload,
                                        onOpenProfiles = onOpenProfiles
                                    )
                                } else if (selectedBouquet != null) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        if (continueWatching != null) {
                                            HubContinueWatchingCard(
                                                stream = continueWatching,
                                                onClick = {
                                                    gatedServiceClick(
                                                        continueWatching.asService(),
                                                        continueWatching.bouquetRef
                                                    )
                                                },
                                                focusRequester = continueWatchingFocus,
                                                downFocus = gridFocus
                                            )
                                        }
                                        // Per bouquet, so scroll position and the restored
                                        // card do not carry over into another bouquet.
                                        key(selectedBouquet.bouquet.reference) {
                                            HubServiceGrid(
                                                bouquetRef = selectedBouquet.bouquet.reference,
                                                services = selectedBouquet.services,
                                                onServiceClick = gatedServiceClick,
                                                modifier = Modifier.weight(1f),
                                                focusRequester = serviceGridFocus,
                                                onServiceInfo = onServiceInfo
                                            )
                                        }
                                    }
                                }

                                is HubSection.Guide -> section.bouquetRef?.let { ref ->
                                    val name = drawerItems?.firstOrNull { it.id == ref }?.title
                                    guideContent(ref, name.orEmpty())
                                }

                                is HubSection.Recordings -> if (movieDir != null) {
                                    if (movieLoading && movieDir !in moviesByLocation) {
                                        Text(
                                            text = stringResource(R.string.loading),
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.testTag("hub_movie_loading")
                                        )
                                    } else {
                                        HubMovieGrid(
                                            movies = moviesByLocation[movieDir].orEmpty(),
                                            onMovieClick = gatedMovieClick
                                        )
                                    }
                                }

                                HubSection.Timers -> timerContent()

                                HubSection.Settings -> HubSettingsRow(
                                    settingsItems = settingsItems,
                                    onSettingsClick = onSettingsClick
                                )
                            }
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (drawerItems.isNullOrEmpty()) {
                        pane()
                    } else {
                        NavigationDrawer(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("hub_drawer"),
                            drawerContent = {
                                HubDrawer(
                                    rows = drawerItems,
                                    selectedId = section.drawerSelection,
                                    iconRes = section.drawerIconRes,
                                    onSelect = onDrawerItemSelected,
                                    onMovedThroughDrawer = { initialGridFocusDone = true },
                                    rightFocus = liveContentFocus
                                )
                            }
                        ) {
                            pane()
                        }
                    }
                }
            }
            if (showStreamUnavailable) {
                TvNeedsReceiverOverlay(onDismiss = { showStreamUnavailable = false })
            }
        }
    }
}

/** A focus request that may find its target out of the composition. */
private fun FocusRequester.tryRequestFocus(): Boolean = try {
    requestFocus()
} catch (_: IllegalStateException) {
    false
}

/** The section's title, the browse state, and its [content] below. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubSectionPane(
    section: HubSection,
    title: String?,
    loading: Boolean,
    errorText: String?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 20.dp)
            .testTag("compose_tv_hub_rows"),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag("hub_title")
            )
        }
        if (loading && (section is HubSection.Live || section is HubSection.Recordings)) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("hub_loading")
            )
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("hub_error")
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * The section's drawer rows. The standard TV drawer selects as focus moves between rows.
 * Focus that merely lands here when content changes (restoration) is not a move and does not
 * select, or the hub would jump to the first row. D-pad Right from the selected bouquet goes
 * to [rightFocus], its grid.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationDrawerScope.HubDrawer(
    rows: List<HubDrawerItem>,
    selectedId: String?,
    iconRes: Int,
    onSelect: (String) -> Unit,
    onMovedThroughDrawer: () -> Unit,
    rightFocus: FocusRequester?
) {
    var lastFocusedId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier
            .padding(vertical = 24.dp)
            .onFocusChanged { focusState ->
                if (!focusState.hasFocus) {
                    lastFocusedId = null
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows, key = { item -> item.id }) { item ->
            NavigationDrawerItem(
                selected = item.id == selectedId,
                onClick = { onSelect(item.id) },
                leadingContent = {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = item.title,
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        modifier = Modifier.testTag("hub_header_icon_${item.id}")
                    )
                },
                colors = dreamDroidTvDrawerItemColors(),
                modifier = Modifier
                    .testTag("hub_header_${item.id}")
                    .focusProperties {
                        if (item.id == selectedId && rightFocus != null) {
                            right = rightFocus
                        }
                    }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            if (lastFocusedId != null && lastFocusedId != item.id) {
                                onMovedThroughDrawer()
                                onSelect(item.id)
                            }
                            lastFocusedId = item.id
                        }
                    }
            ) {
                Text(text = item.title)
            }
        }
    }
}

/**
 * Live TV without bouquets: why ([message], the receiver's error when it gave one), Reload,
 * and Profiles to pick or fix the receiver.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubLiveEmptyState(message: String, onReload: () -> Unit, onOpenProfiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hub_live_empty"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("hub_live_empty_message")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onReload,
                modifier = Modifier
                    .heightIn(min = HubMinTouchTarget)
                    .testTag("hub_live_empty_reload")
            ) {
                Text(text = stringResource(R.string.reload))
            }
            Button(
                onClick = onOpenProfiles,
                modifier = Modifier
                    .heightIn(min = HubMinTouchTarget)
                    .testTag("hub_live_empty_profiles")
            ) {
                Text(text = stringResource(R.string.profiles))
            }
        }
    }
}

/** TV-focusable Offline stream explain. OK dismisses; not a Toast. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNeedsReceiverOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "hub_stream_unavailable"
) {
    val okFocus = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        okFocus.requestFocus()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusGroup()
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.session_needs_receiver),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.session_needs_receiver_long),
                style = MaterialTheme.typography.bodyLarge
            )
            Surface(
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(okFocus)
                    .testTag("${testTag}_ok"),
                colors = dreamDroidTvCardColors(),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape()
            ) {
                Text(
                    text = stringResource(R.string.ok),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * The hub's top bar: the [tabs] (selection follows D-pad moves between tabs; entering the
 * row from the content does not select), the profile + connection chip that opens Profiles,
 * Recheck while the session needs one, and the Settings icon at the far right.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubTopBar(
    tabs: List<HubTab>,
    section: HubSection,
    onOpenTab: (HubTab) -> Unit,
    onOpenSettings: () -> Unit,
    liveTabFocus: FocusRequester,
    profileName: String,
    sessionChipLabel: String?,
    onOpenProfiles: () -> Unit,
    onSessionRecheck: (() -> Unit)?,
    recheckLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 20.dp)
            .testTag("hub_top_bar"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val selectedIndex = tabs.indexOf(section.tab)
        var lastFocusedTab by remember { mutableStateOf<HubTab?>(null) }
        TabRow(
            selectedTabIndex = selectedIndex.coerceAtLeast(0),
            modifier = Modifier
                .weight(1f)
                .focusRestorer()
                .onFocusChanged { focusState ->
                    if (!focusState.hasFocus) {
                        lastFocusedTab = null
                    }
                }
                .testTag("hub_tabs"),
            indicator = { tabPositions, doesTabRowHaveFocus ->
                // Settings has no tab: no indicator then.
                tabPositions.getOrNull(selectedIndex)?.let { position ->
                    TabRowDefaults.PillIndicator(
                        currentTabPosition = position,
                        doesTabRowHaveFocus = doesTabRowHaveFocus
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, tab ->
                key(tab) {
                    Tab(
                        selected = index == selectedIndex,
                        onFocus = {
                            if (lastFocusedTab != null && lastFocusedTab != tab) {
                                onOpenTab(tab)
                            }
                            lastFocusedTab = tab
                        },
                        onClick = { onOpenTab(tab) },
                        modifier = Modifier
                            .heightIn(min = HubMinTouchTarget)
                            .semantics { role = Role.Tab }
                            .testTag("hub_tab_${tab.name.lowercase()}")
                            .then(
                                if (tab == HubTab.Live) {
                                    Modifier.focusRequester(liveTabFocus)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Text(
                            text = stringResource(tab.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
        if (sessionChipLabel != null) {
            HubProfileChip(
                profileName = profileName,
                sessionLabel = sessionChipLabel,
                onClick = onOpenProfiles
            )
        }
        if (onSessionRecheck != null) {
            Surface(
                onClick = onSessionRecheck,
                modifier = Modifier
                    .heightIn(min = HubMinTouchTarget)
                    .testTag("hub_session_recheck"),
                colors = dreamDroidTvCardColors(),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape()
            ) {
                Text(
                    text = recheckLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                )
            }
        }
        val settingsSelected = section == HubSection.Settings
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(HubMinTouchTarget)
                .semantics { selected = settingsSelected }
                .testTag("hub_action_settings")
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_badge_settings),
                contentDescription = stringResource(R.string.settings),
                tint = if (settingsSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                }
            )
        }
    }
}

/**
 * The active profile and the persistent Online / Offline / Checking word in one chip. OK
 * opens Profiles.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubProfileChip(profileName: String, sessionLabel: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = HubMinTouchTarget)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .testTag("hub_profile_chip"),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profileName.isNotEmpty()) {
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .testTag("hub_profile_name")
                )
            }
            Text(
                text = sessionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("hub_session_chip")
            )
        }
    }
}

/** Settings action row — public for focused instrumented tests without drawer focus noise. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubSettingsRow(
    settingsItems: List<Pair<BrowseItem.Kind, String>>,
    onSettingsClick: (BrowseItem.Kind) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub_settings_row")
    ) {
        items(settingsItems, key = { it.first.name }) { (kind, title) ->
            Surface(
                onClick = { onSettingsClick(kind) },
                modifier = Modifier
                    .width(200.dp)
                    .testTag("hub_settings_${kind.name.lowercase()}"),
                colors = dreamDroidTvCardColors(),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape()
            ) {
                Column {
                    Image(
                        painter = painterResource(TvComposeHubHost.settingsBadgeRes(kind)),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("hub_settings_icon_${kind.name.lowercase()}")
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** One bouquet's service/now-next cards (Phase 3.1c-iv-d). Public for Compose tests. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubServiceRow(
    bouquetRef: String,
    services: List<ServiceNowNext>,
    onServiceClick: (ServiceNowNext, String?) -> Unit,
    modifier: Modifier = Modifier,
    currentServiceRef: String? = null,
    firstItemFocusRequester: FocusRequester? = null,
    onUserInteraction: (() -> Unit)? = null,
    onScrollInProgress: ((Boolean) -> Unit)? = null,
    onServiceInfo: ((ServiceNowNext, String?) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentServiceRef, services) {
        val index = services.indexOfFirst { it.serviceReference == currentServiceRef }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }
    if (onScrollInProgress != null) {
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                onScrollInProgress(scrolling)
            }
        }
    }
    val firstCardIndex = services.indexOfFirst { service ->
        !tvHubDrawsMarkerHeader(service.serviceReference)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub_service_row")
            .then(
                if (onUserInteraction != null) {
                    Modifier.onPreviewKeyEvent {
                        onUserInteraction()
                        false
                    }
                } else {
                    Modifier
                }
            )
    ) {
        itemsIndexed(services, key = { _, it -> it.serviceReference }) { index, service ->
            val cardModifier = if (index == firstCardIndex && firstItemFocusRequester != null) {
                Modifier.focusRequester(firstItemFocusRequester)
            } else {
                Modifier
            }
            HubBouquetServiceItem(
                service = service,
                bouquetRef = bouquetRef,
                onServiceClick = onServiceClick,
                modifier = cardModifier,
                onFocused = onUserInteraction,
                onInfo = onServiceInfo
            )
        }
    }
}

/** Selected bouquet as a wrapping grid so the hub pane is not a single strip. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubServiceGrid(
    bouquetRef: String,
    services: List<ServiceNowNext>,
    onServiceClick: (ServiceNowNext, String?) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onServiceInfo: ((ServiceNowNext, String?) -> Unit)? = null
) {
    val firstCardIndex = services.indexOfFirst { service ->
        !tvHubDrawsMarkerHeader(service.serviceReference)
    }
    val firstCardFocus = remember { FocusRequester() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(HubGridItemSpacing),
        verticalArrangement = Arrangement.spacedBy(HubGridItemSpacing),
        contentPadding = PaddingValues(
            start = HubGridFocusInset,
            top = HubGridFocusInset,
            end = HubGridFocusInset,
            bottom = 48.dp
        ),
        modifier = modifier
            .fillMaxSize()
            .testTag("hub_service_grid")
            .then(
                if (focusRequester != null) {
                    // Entering the grid returns to the card focused last, else the first.
                    Modifier
                        .focusRequester(focusRequester)
                        .focusRestorer(firstCardFocus)
                } else {
                    Modifier
                }
            )
    ) {
        gridItemsIndexed(
            services,
            key = { _, it -> it.serviceReference },
            span = { _, service ->
                if (tvHubDrawsMarkerHeader(service.serviceReference)) {
                    GridItemSpan(maxLineSpan)
                } else {
                    GridItemSpan(1)
                }
            }
        ) { index, service ->
            HubBouquetServiceItem(
                service = service,
                bouquetRef = bouquetRef,
                onServiceClick = onServiceClick,
                modifier = if (index == firstCardIndex) {
                    Modifier.focusRequester(firstCardFocus)
                } else {
                    Modifier
                },
                fillWidth = true,
                contentExpanded = true,
                onInfo = onServiceInfo
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubBouquetServiceItem(
    service: ServiceNowNext,
    bouquetRef: String,
    onServiceClick: (ServiceNowNext, String?) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    fillWidth: Boolean = false,
    contentExpanded: Boolean = true,
    onInfo: ((ServiceNowNext, String?) -> Unit)? = null
) {
    if (tvHubDrawsMarkerHeader(service.serviceReference)) {
        HubBouquetMarkerHeader(
            name = service.serviceName,
            fillWidth = fillWidth,
            modifier = modifier
        )
    } else {
        HubServiceCard(
            service = service,
            onClick = { onServiceClick(service, bouquetRef) },
            modifier = modifier,
            onFocused = onFocused,
            fillWidth = fillWidth,
            contentExpanded = contentExpanded,
            onInfo = onInfo?.let { info ->
                { info(service, bouquetRef) }
            }
        )
    }
}

/** [LastLiveStream] as the service the hub's stream path plays. */
private fun LastLiveStream.asService(): ServiceNowNext =
    ServiceNowNext(serviceReference = reference, serviceName = serviceName)

/**
 * "Continue watching": the channel the active profile played last, in a service card's size
 * above Live TV's grid. OK plays it like its card in the grid; Down goes to [downFocus].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubContinueWatchingCard(
    stream: LastLiveStream,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    downFocus: FocusRequester?
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(start = HubGridFocusInset, top = HubGridFocusInset, end = HubGridFocusInset)
            .width(HubServiceRowCardWidth)
            .height(HubServiceCardHeight)
            .focusRequester(focusRequester)
            .focusProperties {
                if (downFocus != null) {
                    down = downFocus
                }
            }
            .semantics(mergeDescendants = true) { role = Role.Button }
            .testTag("hub_continue_watching"),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PiconImage(
                reference = stream.reference,
                name = stream.serviceName,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(width = HubServicePiconWidth, height = HubServicePiconHeight)
                    .testTag("hub_continue_watching_picon")
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tv_hub_continue_watching),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stream.serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("hub_continue_watching_name")
                )
            }
        }
    }
}

/** Name-only bouquet description. Not focusable, so OK does not zap or stream it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubBouquetMarkerHeader(
    name: String,
    fillWidth: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .focusProperties { canFocus = false }
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .testTag("hub_service_marker")
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubServiceCard(
    service: ServiceNowNext,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    fillWidth: Boolean = false,
    contentExpanded: Boolean = false,
    onInfo: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val imageWidthPx = with(density) { 200.dp.roundToPx() }
    val now = service.now
    val next = service.next
    val nowTitle = now?.title.orEmpty()
    val serviceName = service.serviceName
    val title: String
    val contentPrimary: String
    val nextStart: String
    val nextTitle: String
    if (next == null || next.title.isEmpty()) {
        title = if (nowTitle.isNotEmpty()) nowTitle else serviceName
        contentPrimary = ""
        nextStart = ""
        nextTitle = ""
    } else {
        title = serviceName
        contentPrimary = if (nowTitle.isNotEmpty()) nowTitle else serviceName
        nextStart = next.startTimeReadable
        nextTitle = next.title
    }
    val progress = nowProgress(now)
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(
                if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(HubServiceRowCardWidth)
            )
            .height(HubServiceCardHeight)
            .testTag("hub_service_card")
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused?.invoke()
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (onInfo == null) {
                    false
                } else if (
                    keyEvent.key == Key.Info ||
                    keyEvent.key == Key.Menu
                ) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        onInfo()
                    }
                    true
                } else {
                    false
                }
            },
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (progress.isKnown) {
                // Card-top strip: opt out of the Material 3 track, gap and stop indicator,
                // like the phone service row.
                LinearProgressIndicator(
                    progress = { progress.elapsed.toFloat() / progress.max.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HubServiceProgressHeight)
                        .testTag("hub_service_progress"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PiconImage(
                    reference = service.serviceReference,
                    name = service.serviceName,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(width = HubServicePiconWidth, height = HubServicePiconHeight)
                        .testTag("hub_service_picon")
                )
                ImageCardContent(
                    title = title,
                    contentPrimary = contentPrimary,
                    nextStart = nextStart,
                    nextTitle = nextTitle,
                    contentExpanded = contentExpanded,
                    fillWidth = true,
                    imageWidthPx = imageWidthPx,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubMovieGrid(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(HubGridItemSpacing),
        verticalArrangement = Arrangement.spacedBy(HubGridItemSpacing),
        contentPadding = PaddingValues(
            start = HubGridFocusInset,
            top = HubGridFocusInset,
            end = HubGridFocusInset,
            bottom = 48.dp
        ),
        modifier = modifier
            .fillMaxSize()
            .testTag("hub_movie_grid")
    ) {
        gridItemsIndexed(movies, key = { _, it -> it.reference + "|" + it.fileName }) { _, movie ->
            HubMovieCard(
                movie = movie,
                onClick = { onMovieClick(movie) },
                fillWidth = true
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubMovieCard(movie: Movie, onClick: () -> Unit, fillWidth: Boolean = false) {
    val descriptionEx = movie.descriptionExtended.replace("\\n", "\n")
    val content = if (descriptionEx.isNotEmpty()) descriptionEx else movie.description
    Surface(
        onClick = onClick,
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(200.dp))
            .height(160.dp)
            .testTag("hub_movie_card"),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hub_movie_card_title")
            )
            if (content.isNotEmpty()) {
                FittedEllipsisText(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}
