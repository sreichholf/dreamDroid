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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.R
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
import net.reichholf.dreamdroid.ui.compose.TvButton
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.services.nowProgress
import net.reichholf.dreamdroid.ui.text.asString
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTopBarItemScale
import net.reichholf.dreamdroid.ui.theme.LocalTvContentInset
import net.reichholf.dreamdroid.ui.theme.TvContentInset
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvBackgroundBrush
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvDrawerItemColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvIconButtonColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvPillColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvTabColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvTopBarItemColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvTopBarItemShape

/**
 * Phase 3.1c-iv Compose TV hub host.
 * - **iv-b..e:** Compose hub beachhead through movie rows.
 * - **iv-f:** Compose hub is the TV default; Leanback browse path removed.
 *   Stream intents honor the integrated/external player pref (`androidx.leanback`
 *   removed).
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

    fun install(activity: ComponentActivity, onAddProfile: () -> Unit) {
        activity.setContent {
            TvHubNavHost(activity = activity, onAddProfile = onAddProfile)
        }
    }
}

data class HubBouquetRow(val bouquet: Service, val services: List<ServiceNowNext>)

/** The Continue watching item of Live TV's grid. */
private const val HUB_GRID_HEADER_KEY = "hub_continue_watching"

/** 5% focus scale needs inset so the first grid row is not clipped by the title. */
private val HubGridItemSpacing = 24.dp
private val HubGridFocusInset = 16.dp

/** One compact card size for the hub grid and the zap overlay. */
internal val HubServiceCardHeight = 120.dp
private val HubServiceProgressHeight = 8.dp
private val HubServicePiconWidth = 80.dp
private val HubServicePiconHeight = 48.dp

/** Zap overlay row card: wide enough for text next to the leading picon. */
internal val HubServiceRowCardWidth = 300.dp

/** Every top bar control: tabs, profile chip, Recheck and Settings. */
internal val HubTopBarHeight = 36.dp

/** Above the first drawer row and the section title, which line up. */
private val HubDrawerVerticalPadding = 24.dp

/** The pane's screen margin, also reported through [LocalTvContentInset]. */
private val HubSectionHorizontalInset = 32.dp

/** The least space between the centred tabs and the top bar's trailing group. */
private val HubTopBarMinGap = 16.dp

/** Top bar icons match the labels beside them. */
private val HubTopBarIconSize = 20.dp

/**
 * True while the user moves between the hub's top tabs. The section content a tab switch
 * reveals must not take focus itself: focus stays on the bar until the user presses Down to
 * enter the section they picked.
 */
internal val LocalTvHubTabNavigation = compositionLocalOf { false }

@Composable
fun ComposeTvHubApp(
    activity: ComponentActivity,
    gate: TvSessionGate?,
    onRecheckProfile: () -> Unit,
    onAddProfile: () -> Unit,
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

    if (gate != TvSessionGate.None) {
        DreamDroidTvTheme {
            TvProfileCheckScreen(
                gate = gate,
                onRecheck = onRecheckProfile,
                onProfiles = onOpenProfiles,
                onAddProfile = onAddProfile
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
            continueWatching = uiState.continueWatching,
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
    continueWatching: ContinueWatching? = null,
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
    // Set while a tab move changes the section, and cleared when focus leaves the tab row:
    // content the new section composes skips its own focus request meanwhile.
    var tabNavigation by remember { mutableStateOf(false) }
    val tabs = hubTabs(bouquetRows, movieLocations)
    val unnamedBouquet = stringResource(R.string.services)
    val drawerItems = section.drawerItems(bouquetRows, movieLocations, unnamedBouquet)
    val selectedBouquet = (section as? HubSection.Live)?.bouquetRef?.let { ref ->
        bouquetRows.firstOrNull { it.bouquet.reference == ref }
    }
    val movieDir = (section as? HubSection.Recordings)?.location
    // Live TV's grid starts with "Continue watching", when a channel was saved for the active
    // profile; it scrolls away with the services.
    val continueWatching = continueWatching.takeIf { selectedBouquet != null }
    // On start the hub focuses Live TV's first card (Continue watching, else the first
    // service) so the remote acts on content instead of an unclear cursor. One-shot per hub
    // entry, and never once the user has moved through the drawer, so late-loading rows do
    // not pull focus out of it.
    val serviceGridFocus = remember { FocusRequester() }
    val continueWatchingFocus = remember { FocusRequester() }
    val gridFocus = serviceGridFocus.takeIf {
        selectedBouquet?.services?.isNotEmpty() == true || continueWatching != null
    }
    val liveContentFocus = if (continueWatching != null) continueWatchingFocus else gridFocus
    val liveTabFocus = remember { FocusRequester() }
    var initialGridFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(selectedBouquet?.bouquet?.reference, liveContentFocus) {
        if (!initialGridFocusDone && liveContentFocus != null && !tabNavigation) {
            // The grid composes its cards during layout; wait for that frame.
            withFrameNanos {}
            initialGridFocusDone = liveContentFocus.tryRequestFocus()
        }
    }
    // Back lands on Live TV's grid: its last focused card, else what the initial focus takes
    // (Continue watching, else the first service); without a grid, on the Live TV tab.
    var focusLiveAfterBack by remember { mutableStateOf(false) }
    BackHandler(enabled = section !is HubSection.Live) {
        focusLiveAfterBack = true
        onBack()
    }
    LaunchedEffect(focusLiveAfterBack, section) {
        if (focusLiveAfterBack && section is HubSection.Live) {
            withFrameNanos {}
            if (!serviceGridFocus.tryRequestFocus()) {
                liveTabFocus.tryRequestFocus()
            }
            // Cleared only now: the flag is this effect's key, so clearing it before the
            // frame restarted the effect and cancelled the request.
            focusLiveAfterBack = false
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
                    .background(dreamDroidTvBackgroundBrush())
                    .testTag("compose_tv_hub_chrome")
            ) {
                HubTopBar(
                    tabs = tabs,
                    section = section,
                    onOpenTab = { tab ->
                        tabNavigation = true
                        onOpenTab(tab)
                    },
                    onTabRowFocusChanged = { focused -> if (!focused) tabNavigation = false },
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
                                    // Per bouquet, so scroll position and the restored card
                                    // do not carry over into another bouquet.
                                    key(selectedBouquet.bouquet.reference) {
                                        HubServiceGrid(
                                            bouquetRef = selectedBouquet.bouquet.reference,
                                            services = selectedBouquet.services,
                                            onServiceClick = gatedServiceClick,
                                            focusRequester = serviceGridFocus,
                                            onServiceInfo = onServiceInfo,
                                            headerFocus = continueWatchingFocus.takeIf {
                                                continueWatching != null
                                            },
                                            header = continueWatching?.let { last ->
                                                { firstCardFocus ->
                                                    HubContinueWatchingCard(
                                                        service = last.service,
                                                        onClick = {
                                                            gatedServiceClick(
                                                                last.service,
                                                                last.bouquetRef
                                                            )
                                                        },
                                                        focusRequester = continueWatchingFocus,
                                                        downFocus = firstCardFocus
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }

                                is HubSection.Guide -> section.bouquetRef?.let { ref ->
                                    val name = bouquetRows
                                        .firstOrNull { it.bouquet.reference == ref }
                                        ?.bouquet?.name
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
                // A tab switch leaves focus on the bar: content the new section composes
                // skips its own focus request until focus leaves the tabs.
                val sectionContent: @Composable () -> Unit = {
                    CompositionLocalProvider(LocalTvHubTabNavigation provides tabNavigation) {
                        pane()
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (drawerItems.isNullOrEmpty()) {
                        sectionContent()
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
                                    rightFocus = gridFocus
                                )
                            }
                        ) {
                            sectionContent()
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
            .padding(
                start = HubSectionHorizontalInset,
                end = HubSectionHorizontalInset,
                top = HubDrawerVerticalPadding,
                bottom = 20.dp
            )
            .testTag("compose_tv_hub_rows"),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (title != null) {
            // As tall as a drawer row and as far down as the first one, so the title lines up
            // with the selected row's icon beside it.
            Box(
                modifier = Modifier.height(NavigationDrawerItemDefaults.ContainerHeightOneLine),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.singleLineCentered(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("hub_title")
                )
            }
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
            // The pane owns the screen margin; a form hosted here subtracts it from its own
            // (LocalTvContentInset), so the timer editor is indented once, not twice.
            CompositionLocalProvider(
                LocalTvContentInset provides TvContentInset(
                    horizontal = HubSectionHorizontalInset,
                    top = HubDrawerVerticalPadding
                )
            ) {
                content()
            }
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
            .padding(vertical = HubDrawerVerticalPadding)
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
            TvButton(
                label = stringResource(R.string.reload),
                onClick = onReload,
                modifier = Modifier.testTag("hub_live_empty_reload")
            )
            TvButton(
                label = stringResource(R.string.profiles),
                onClick = onOpenProfiles,
                modifier = Modifier.testTag("hub_live_empty_profiles")
            )
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
 * Recheck while the session needs one, and the Settings icon at the far right. Every control
 * is [HubTopBarHeight] tall with its content centred; [HubTopBarLayout] places the tabs.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubTopBar(
    tabs: List<HubTab>,
    section: HubSection,
    onOpenTab: (HubTab) -> Unit,
    onTabRowFocusChanged: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    liveTabFocus: FocusRequester,
    profileName: String,
    sessionChipLabel: String?,
    onOpenProfiles: () -> Unit,
    onSessionRecheck: (() -> Unit)?,
    recheckLabel: String
) {
    val selectedIndex = tabs.indexOf(section.tab)
    var lastFocusedTab by remember { mutableStateOf<HubTab?>(null) }
    val pillColors = dreamDroidTvPillColors()
    HubTopBarLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 20.dp)
            .testTag("hub_top_bar"),
        tabs = {
            TabRow(
                selectedTabIndex = selectedIndex.coerceAtLeast(0),
                modifier = Modifier
                    .focusRestorer()
                    .onFocusChanged { focusState ->
                        if (!focusState.hasFocus) {
                            lastFocusedTab = null
                        }
                        onTabRowFocusChanged(focusState.hasFocus)
                    }
                    .testTag("hub_tabs"),
                indicator = { tabPositions, doesTabRowHaveFocus ->
                    // Settings has no tab: no indicator then.
                    tabPositions.getOrNull(selectedIndex)?.let { position ->
                        TabRowDefaults.PillIndicator(
                            currentTabPosition = position,
                            doesTabRowHaveFocus = doesTabRowHaveFocus,
                            activeColor = pillColors.active,
                            inactiveColor = pillColors.inactive
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
                            colors = dreamDroidTvTabColors(rowHasFocus = hasFocus),
                            modifier = Modifier
                                .height(HubTopBarHeight)
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
                            HubTopBarLabel(
                                text = stringResource(tab.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                horizontalPadding = 12.dp
                            )
                        }
                    }
                }
            }
        },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                            .height(HubTopBarHeight)
                            .testTag("hub_session_recheck"),
                        colors = dreamDroidTvTopBarItemColors(),
                        scale = DreamDroidTvTopBarItemScale,
                        shape = dreamDroidTvTopBarItemShape()
                    ) {
                        HubTopBarLabel(
                            text = recheckLabel,
                            style = MaterialTheme.typography.labelLarge,
                            horizontalPadding = 12.dp
                        )
                    }
                }
                val settingsSelected = section == HubSection.Settings
                IconButton(
                    onClick = onOpenSettings,
                    colors = dreamDroidTvIconButtonColors(),
                    modifier = Modifier
                        .size(HubTopBarHeight)
                        .semantics { selected = settingsSelected }
                        .testTag("hub_action_settings")
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(R.string.settings),
                        modifier = Modifier.size(HubTopBarIconSize),
                        tint = if (settingsSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        }
                    )
                }
            }
        }
    )
}

/**
 * A top bar label, [HubTopBarHeight] tall and centred. tv-material surfaces and tabs align
 * their content to the top, so a shorter label would sit above the control's centre.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubTopBarLabel(text: String, style: TextStyle, horizontalPadding: Dp) {
    Box(
        modifier = Modifier
            .height(HubTopBarHeight)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = style.singleLineCentered(), maxLines = 1)
    }
}

/**
 * Places [tabs] centred on the bar's full width when they fit between the start and the
 * [trailing] group with [HubTopBarMinGap] to spare, else at the start; [trailing] sits at the
 * end. Both are centred vertically. The tv-material `TabRow` is only as wide as its tabs, so a
 * weighted slot would centre it in the leftover space instead.
 */
@Composable
private fun HubTopBarLayout(
    tabs: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(contents = listOf(tabs, trailing), modifier = modifier) { measurables, constraints ->
        val (tabsMeasurables, trailingMeasurables) = measurables
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val width = constraints.maxWidth
        val gap = HubTopBarMinGap.roundToPx()
        val trailingPlaceable = trailingMeasurables.single().measure(loose)
        val tabsPlaceable = tabsMeasurables.single().measure(
            loose.copy(maxWidth = (width - trailingPlaceable.width - gap).coerceAtLeast(0))
        )
        val height = maxOf(tabsPlaceable.height, trailingPlaceable.height)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            tabsPlaceable.place(
                x = hubTabsStart(width, tabsPlaceable.width, trailingPlaceable.width, gap),
                y = (height - tabsPlaceable.height) / 2
            )
            trailingPlaceable.place(
                x = width - trailingPlaceable.width,
                y = (height - trailingPlaceable.height) / 2
            )
        }
    }
}

/**
 * Where the tabs start in a bar of [barWidth]: centred when that leaves [minGap] before the
 * [trailingWidth] group at the end, else at the start.
 */
internal fun hubTabsStart(barWidth: Int, tabsWidth: Int, trailingWidth: Int, minGap: Int): Int {
    val centred = (barWidth - tabsWidth) / 2
    val fits = centred >= 0 && centred + tabsWidth + minGap <= barWidth - trailingWidth
    return if (fits) centred else 0
}

/**
 * One line centred in its box: the line height's extra space is split evenly and trimmed, so
 * the glyphs sit on the box's centre instead of above it.
 */
private fun TextStyle.singleLineCentered(): TextStyle = copy(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

/**
 * The active profile and the persistent Online / Offline / Checking word in one chip. OK
 * opens Profiles.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubProfileChip(profileName: String, sessionLabel: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(HubTopBarHeight)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .testTag("hub_profile_chip"),
        colors = dreamDroidTvTopBarItemColors(),
        scale = DreamDroidTvTopBarItemScale,
        shape = dreamDroidTvTopBarItemShape(),
        interactionSource = interactionSource
    ) {
        // The surface aligns its content to the top: fill its height and centre the labels.
        Row(
            modifier = Modifier
                .height(HubTopBarHeight)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profileName.isNotEmpty()) {
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.labelLarge.singleLineCentered(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .testTag("hub_profile_name")
                )
            }
            Text(
                text = sessionLabel,
                style = MaterialTheme.typography.labelLarge.singleLineCentered(),
                // Primary blue on the light focus pill is too faint: the label colour then.
                color = if (focused) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
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
        // The row clips its items at its edges; the inset leaves room for the 5% focus scale,
        // so the focused end card keeps its rounded corners.
        contentPadding = PaddingValues(horizontal = HubGridFocusInset),
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
        itemsIndexed(
            services,
            key = { index, it -> "$index:${it.serviceReference}" }
        ) { index, service ->
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

/**
 * Selected bouquet as a wrapping grid so the hub pane is not a single strip. [header], given
 * the first card's requester for D-pad Down (null without cards), is the grid's first item across the full line
 * and scrolls with it; entering the grid without a card focused before lands on
 * [headerFocus], its requester, else on the first card.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubServiceGrid(
    bouquetRef: String,
    services: List<ServiceNowNext>,
    onServiceClick: (ServiceNowNext, String?) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onServiceInfo: ((ServiceNowNext, String?) -> Unit)? = null,
    headerFocus: FocusRequester? = null,
    header: (@Composable (firstCardFocus: FocusRequester?) -> Unit)? = null
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
                        .focusRestorer(headerFocus ?: firstCardFocus)
                } else {
                    Modifier
                }
            )
    ) {
        if (header != null) {
            item(key = HUB_GRID_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                header(firstCardFocus.takeIf { firstCardIndex >= 0 })
            }
        }
        gridItemsIndexed(
            services,
            key = { index, it -> "$index:${it.serviceReference}" },
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
                    Modifier
                        .focusRequester(firstCardFocus)
                        .focusProperties {
                            if (headerFocus != null) {
                                up = headerFocus
                            }
                        }
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

/**
 * "Continue watching": the channel the active profile played last, in a service card's size,
 * as the first item of Live TV's grid: its name, the programme running now and its progress.
 * OK plays it like its card in the grid; Down goes to [downFocus], the first card.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubContinueWatchingCard(
    service: ServiceNowNext,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    downFocus: FocusRequester?
) {
    var focused by remember { mutableStateOf(false) }
    val nowTitle = service.now?.title.orEmpty()
    val progress = nowProgress(service.now)
    Surface(
        onClick = onClick,
        modifier = Modifier
            // The grid gives a full-line item its whole width as minimum: unwrap it, or the
            // card stretches across the line and its focus scale passes the grid's inset.
            .wrapContentWidth(Alignment.Start)
            .width(HubServiceRowCardWidth)
            .height(HubServiceCardHeight)
            .focusRequester(focusRequester)
            .focusProperties {
                if (downFocus != null) {
                    down = downFocus
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) { role = Role.Button }
            .testTag("hub_continue_watching"),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (progress.isKnown) {
                LinearProgressIndicator(
                    progress = { progress.elapsed.toFloat() / progress.max.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HubServiceProgressHeight)
                        .testTag("hub_continue_watching_progress"),
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
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(width = HubServicePiconWidth, height = HubServicePiconHeight)
                        .testTag("hub_continue_watching_picon")
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tv_hub_continue_watching),
                        style = MaterialTheme.typography.labelMedium,
                        // The accent reads on the resting card only; the focused one takes
                        // its content colour.
                        color = if (focused) {
                            LocalContentColor.current
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = service.serviceName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("hub_continue_watching_name")
                    )
                    if (nowTitle.isNotEmpty()) {
                        Text(
                            text = nowTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("hub_continue_watching_now")
                        )
                    }
                }
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
        gridItemsIndexed(movies, key = { index, it ->
            "$index:${it.reference}|${it.fileName}"
        }) { _, movie ->
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
