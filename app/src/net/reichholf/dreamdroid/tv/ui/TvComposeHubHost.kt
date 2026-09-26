package net.reichholf.dreamdroid.tv.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.tv.activities.MainActivity
import net.reichholf.dreamdroid.tv.view.FittedEllipsisText
import net.reichholf.dreamdroid.tv.view.ImageCardContent
import net.reichholf.dreamdroid.ui.nav.ShellMessages
import net.reichholf.dreamdroid.ui.nav.mutationResultText
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvDrawerItemColors
import net.reichholf.dreamdroid.video.startLiveServiceStream

/**
 * Phase 3.1c-iv Compose TV hub host.
 * - **iv-b..e:** Compose hub beachhead through movie rows.
 * - **iv-f:** Compose hub is the TV default; Leanback browse path removed.
 *   Stream intents honor the integrated/external player pref. Overlay zap list
 *   is Compose TV cards (`androidx.leanback` removed).
 */
object TvComposeHubHost {
    const val HEADER_SETTINGS_ID: String = "settings"
    const val HEADER_TIMERS_ID: String = "timers"
    const val HEADER_MULTIEPG_ID: String = "multiepg"
    const val HEADER_PLACEHOLDER_ID: String = "placeholder"
    const val HEADER_MOVIE_PREFIX: String = "movie:"

    /** Same bouquet query formerly on RootBrowseFragment.BOUQUETS_TV. */
    const val BOUQUETS_TV: String =
        "1:7:1:0:0:0:0:0:0:0:(type == 1) || (type == 17) || (type == 195) || " +
            "(type == 25) FROM BOUQUET \\\"bouquets.tv\\\" ORDER BY bouquet"

    fun movieHeaderId(dirname: String): String = HEADER_MOVIE_PREFIX + dirname

    fun movieDirnameFromHeader(headerId: String): String? =
        headerId.takeIf { it.startsWith(HEADER_MOVIE_PREFIX) }
            ?.removePrefix(HEADER_MOVIE_PREFIX)
            ?.takeIf { it.isNotEmpty() }

    fun destinationForKind(kind: BrowseItem.Kind): Any? = when (kind) {
        BrowseItem.Kind.Preferences -> TvSettings
        BrowseItem.Kind.Profile -> TvProfiles
        BrowseItem.Kind.Reload -> null
    }

    /** Blank ref or name is omitted, matching a launch that used to send no extras. */
    fun tvMultiEpgRoute(bouquetRef: String? = null, bouquetName: String? = null): TvMultiEpg =
        TvMultiEpg(
            bouquetRef = bouquetRef?.takeIf { it.isNotBlank() }.orEmpty(),
            bouquetName = bouquetName?.takeIf { it.isNotBlank() }.orEmpty()
        )

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

    /** Survives hub reload so Settings / Timers / MultiEPG are not bounced away. */
    fun isPersistentHubHeader(headerId: String): Boolean = headerId == HEADER_SETTINGS_ID ||
        headerId == HEADER_TIMERS_ID ||
        headerId == HEADER_MULTIEPG_ID

    /** Collapsed TV drawer shows only this; empty leading content is a nameless blue disc. */
    fun hubHeaderIconRes(headerId: String): Int = when {
        headerId == HEADER_SETTINGS_ID -> R.drawable.ic_badge_settings
        headerId == HEADER_TIMERS_ID -> R.drawable.ic_menu_timer
        headerId == HEADER_MULTIEPG_ID -> R.drawable.ic_multiepg
        headerId.startsWith(HEADER_MOVIE_PREFIX) -> R.drawable.ic_menu_movie
        else -> R.drawable.ic_menu_tv
    }

    fun streamServiceIntent(
        context: Context,
        service: ServiceNowNext,
        bouquetRef: String?
    ): Intent {
        val title = service.now?.title?.takeIf { it.isNotEmpty() } ?: service.serviceName
        return IntentFactory.getStreamServiceIntent(
            context,
            service.serviceReference,
            title,
            bouquetRef,
            service
        )
    }

    fun streamMovieIntent(context: Context, movie: Movie): Intent =
        IntentFactory.getStreamFileIntent(
            context,
            movie.reference,
            movie.fileName,
            movie.title,
            movie
        )

    fun startStreamIntent(activity: Activity, intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.missing_stream_player, Toast.LENGTH_LONG).show()
        }
    }

    fun shouldShowBrowseError(
        selectedHeaderId: String,
        loading: Boolean,
        errorText: String?,
        hasPaintedContent: Boolean = false
    ): Boolean = shouldShowTvBrowseError(
        selectedHeaderId,
        loading,
        errorText,
        hasPaintedContent
    )

    fun install(activity: ComponentActivity) {
        val host = activity as? MainActivity
        activity.setContent {
            TvHubNavHost(
                activity = activity,
                onRecheckProfile = { host?.recheckProfile() }
            )
        }
    }
}

data class HubNavHeader(val id: String, val title: String)

data class HubBouquetRow(val bouquet: Service, val services: List<ServiceNowNext>)

/** 5% focus scale needs inset so the first grid row is not clipped by the title. */
private val HubGridItemSpacing = 24.dp
private val HubGridFocusInset = 16.dp
internal val HubServiceGridCardHeight = 220.dp

@Composable
fun ComposeTvHubApp(
    activity: ComponentActivity,
    onRecheckProfile: () -> Unit = { (activity as? MainActivity)?.recheckProfile() },
    viewModel: TvHubViewModel = viewModel(factory = TvHubViewModel.Factory),
    onOpenSettings: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onOpenMultiEpg: (reference: String, name: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val status by SessionConnectionHolder.shared.status.collectAsStateWithLifecycle()
    val profile = DreamDroid.getCurrentProfile()
    // One blocking Room read seeds the gate so Checking never flashes ProfileCheck.
    // Later refreshes run on IO when the profile or session changes.
    var hasCache by remember(profile.id) {
        mutableStateOf(hasUseDrivenCache(profile, context))
    }
    LaunchedEffect(profile.id, status.session) {
        hasCache = withContext(Dispatchers.IO) {
            hasUseDrivenCache(profile, context)
        }
    }
    val streamingEnabled = status.allowsStreaming()
    val failedMessage = status.lastFailure?.userMessage(context)?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.connection_error)
    val gate = tvSessionGate(
        status = status,
        hasCache = hasCache,
        checkingMessage = stringResource(R.string.checking_connection),
        failedTitle = String.format("%s@%s:%s", profile.user, profile.host, profile.port),
        failedMessage = failedMessage
    )
    val settingsTitle = stringResource(R.string.preferences)
    val timersTitle = stringResource(R.string.timer)
    val multiEpgTitle = stringResource(R.string.multiepg)
    val placeholderTitle = stringResource(R.string.services)
    val bouquetRows = viewModel.bouquetRows
    val movieLocations = viewModel.movieLocations
    val selectedHeaderId = viewModel.selectedHeaderId

    val headers = remember(
        settingsTitle,
        timersTitle,
        multiEpgTitle,
        placeholderTitle,
        bouquetRows,
        movieLocations
    ) {
        buildList {
            add(HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, settingsTitle))
            add(HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, timersTitle))
            add(
                HubNavHeader(
                    TvComposeHubHost.HEADER_MULTIEPG_ID,
                    multiEpgTitle
                )
            )
            if (bouquetRows.isEmpty()) {
                add(HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, placeholderTitle))
            } else {
                bouquetRows.forEach { row ->
                    val title = row.bouquet.name.ifBlank { placeholderTitle }
                    add(HubNavHeader(row.bouquet.reference, title))
                }
            }
            movieLocations.forEach { dirname ->
                add(HubNavHeader(TvComposeHubHost.movieHeaderId(dirname), dirname))
            }
        }
    }
    val settingsItems = TvComposeHubHost.defaultSettingsKinds().map { kind ->
        kind to stringResource(TvComposeHubHost.settingsTitleRes(kind))
    }
    val openProfiles = onOpenProfiles

    if (gate is TvSessionGate.Checking || gate is TvSessionGate.Failed) {
        DreamDroidTvTheme {
            TvProfileCheckScreen(
                gate = gate,
                onRecheck = onRecheckProfile,
                onProfiles = openProfiles
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ComposeTvHubChrome(
            headers = headers,
            selectedHeaderId = selectedHeaderId,
            onHeaderSelected = viewModel::selectHeader,
            settingsItems = settingsItems,
            onSettingsClick = { kind ->
                when (kind) {
                    BrowseItem.Kind.Reload -> viewModel.reload()
                    BrowseItem.Kind.Preferences -> onOpenSettings()
                    BrowseItem.Kind.Profile -> onOpenProfiles()
                }
            },
            bouquetRows = bouquetRows,
            moviesByLocation = viewModel.moviesByLocation,
            loading = viewModel.loading,
            movieLoading = viewModel.movieLoading,
            errorText = viewModel.errorText ?: viewModel.movieError,
            streamingEnabled = streamingEnabled,
            onServiceClick = { service, bouquetRef ->
                openServiceStream(activity, service, bouquetRef)
            },
            onMovieClick = { movie ->
                openMovieStream(activity, movie)
            },
            onOpenMultiEpg = onOpenMultiEpg,
            sessionChipLabel = stringResource(status.chipLabelRes()),
            onSessionRecheck = if (shouldShowTvSessionRecheck(status)) {
                onRecheckProfile
            } else {
                null
            },
            mutationsBlocked = status.blocksMutations,
            onServiceInfo = { service, bouquetRef ->
                viewModel.showServiceTimer(service, bouquetRef)
            }
        )
        val overlayTarget = viewModel.serviceTimerTarget
        val editorEvent = viewModel.editTimerEvent
        // Drop the INFO overlay while the editor is open so D-pad reaches the form
        // (same as MultiEPG dismissing detail before TvTimerEditorHost).
        if (overlayTarget != null && editorEvent == null) {
            DreamDroidTvTheme {
                TvServiceTimerOverlay(
                    service = overlayTarget.first,
                    onDismiss = viewModel::dismissServiceTimer,
                    onStream = {
                        openServiceStream(
                            activity,
                            overlayTarget.first,
                            overlayTarget.second
                        )
                        viewModel.dismissServiceTimer()
                    },
                    onSetTimer = { event ->
                        setTimerFromEvent(activity, event) {
                            viewModel.dismissServiceTimer()
                        }
                    },
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
                    onSaved = {
                        viewModel.dismissEditTimer()
                        viewModel.dismissServiceTimer()
                    },
                    mutationsBlocked = status.blocksMutations
                )
            }
        }
    }
}

private fun openServiceStream(
    activity: ComponentActivity,
    service: ServiceNowNext,
    bouquetRef: String?
) {
    activity.startLiveServiceStream(activity, service.serviceReference) {
        TvComposeHubHost.startStreamIntent(
            activity,
            TvComposeHubHost.streamServiceIntent(activity, service, bouquetRef)
        )
    }
}

private fun openMovieStream(activity: ComponentActivity, movie: Movie) {
    TvComposeHubHost.startStreamIntent(
        activity,
        TvComposeHubHost.streamMovieIntent(activity, movie)
    )
}

private fun setTimerFromEvent(activity: ComponentActivity, event: Event, onDone: () -> Unit) {
    activity.launchSimpleResultLoad(
        TimerAddByEventIdRequestHandler(),
        Timer.getEventIdParams(event)
    ) { _, result, error ->
        ShellMessages.post(
            mutationResultText(
                stateText = result.stateText,
                errorText = error?.resolve(activity),
                fallback = activity.getString(R.string.get_content_error)
            )
        )
        onDone()
    }
}

/** Side headers ([NavigationDrawer]) + row list focus chrome (Phase 3.1c-iv-c/d/e). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ComposeTvHubChrome(
    headers: List<HubNavHeader>,
    selectedHeaderId: String,
    onHeaderSelected: (String) -> Unit,
    settingsItems: List<Pair<BrowseItem.Kind, String>>,
    onSettingsClick: (BrowseItem.Kind) -> Unit,
    modifier: Modifier = Modifier,
    bouquetRows: List<HubBouquetRow> = emptyList(),
    moviesByLocation: Map<String, List<Movie>> = emptyMap(),
    loading: Boolean = false,
    movieLoading: Boolean = false,
    errorText: String? = null,
    streamingEnabled: Boolean = true,
    onServiceClick: (ServiceNowNext, String?) -> Unit = { _, _ -> },
    onMovieClick: (Movie) -> Unit = {},
    onOpenMultiEpg: (reference: String, name: String) -> Unit = { _, _ -> },
    sessionChipLabel: String? = null,
    onSessionRecheck: (() -> Unit)? = null,
    sessionRecheckLabel: String? = null,
    mutationsBlocked: Boolean = false,
    onServiceInfo: ((ServiceNowNext, String?) -> Unit)? = null,
    timerContent: @Composable () -> Unit = {
        TvTimerHost(
            modifier = Modifier.fillMaxSize(),
            mutationsBlocked = mutationsBlocked
        )
    }
) {
    var showStreamUnavailable by remember { mutableStateOf(false) }
    val selectedBouquet = bouquetRows.firstOrNull { it.bouquet.reference == selectedHeaderId }
    val movieDir = TvComposeHubHost.movieDirnameFromHeader(selectedHeaderId)
    val hasPaintedContent = selectedBouquet?.services?.isNotEmpty() == true ||
        (movieDir != null && moviesByLocation[movieDir].orEmpty().isNotEmpty()) ||
        (
            selectedHeaderId == TvComposeHubHost.HEADER_MULTIEPG_ID &&
                bouquetRows.isNotEmpty()
            )
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
            NavigationDrawer(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("compose_tv_hub_chrome"),
                drawerContent = {
                    LazyColumn(
                        modifier = Modifier.padding(vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(headers, key = { header -> header.id }) { header ->
                            NavigationDrawerItem(
                                selected = header.id == selectedHeaderId,
                                onClick = { onHeaderSelected(header.id) },
                                leadingContent = {
                                    Image(
                                        painter = painterResource(
                                            TvComposeHubHost.hubHeaderIconRes(header.id)
                                        ),
                                        contentDescription = header.title,
                                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                                        modifier = Modifier.testTag("hub_header_icon_${header.id}")
                                    )
                                },
                                colors = dreamDroidTvDrawerItemColors(),
                                modifier = Modifier
                                    .testTag("hub_header_${header.id}")
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            onHeaderSelected(header.id)
                                        }
                                    }
                            ) {
                                Text(text = header.title)
                            }
                        }
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("compose_tv_hub_rows"),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    val title = headers.firstOrNull { header ->
                        header.id == selectedHeaderId
                    }?.title.orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedBouquet != null) {
                                Surface(
                                    onClick = {
                                        val bouquet = selectedBouquet.bouquet
                                        onOpenMultiEpg(
                                            bouquet.reference,
                                            bouquet.name.ifBlank { bouquet.reference }
                                        )
                                    },
                                    modifier = Modifier.testTag("hub_bouquet_multiepg"),
                                    colors = dreamDroidTvCardColors(),
                                    scale = ClickableSurfaceDefaults.scale(
                                        focusedScale = 1.05f
                                    ),
                                    shape = ClickableSurfaceDefaults.shape()
                                ) {
                                    Image(
                                        painter = painterResource(
                                            R.drawable.ic_multiepg
                                        ),
                                        contentDescription = stringResource(
                                            R.string.multiepg
                                        ),
                                        colorFilter = ColorFilter.tint(
                                            LocalContentColor.current
                                        ),
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(32.dp)
                                    )
                                }
                            }
                            if (sessionChipLabel != null) {
                                HubSessionStatus(
                                    label = sessionChipLabel,
                                    recheckLabel = sessionRecheckLabel
                                        ?: stringResource(R.string.recheck),
                                    onRecheck = onSessionRecheck
                                )
                            }
                        }
                    }
                    if (loading && selectedHeaderId != TvComposeHubHost.HEADER_TIMERS_ID) {
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("hub_loading")
                        )
                    }
                    if (
                        TvComposeHubHost.shouldShowBrowseError(
                            selectedHeaderId,
                            loading,
                            errorText,
                            hasPaintedContent
                        )
                    ) {
                        Text(
                            text = errorText.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("hub_error")
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (selectedHeaderId == TvComposeHubHost.HEADER_TIMERS_ID) {
                            timerContent()
                        } else if (selectedHeaderId == TvComposeHubHost.HEADER_SETTINGS_ID) {
                            HubSettingsRow(
                                settingsItems = settingsItems,
                                onSettingsClick = onSettingsClick
                            )
                        } else if (selectedHeaderId == TvComposeHubHost.HEADER_MULTIEPG_ID) {
                            HubMultiEpgBouquetGrid(
                                bouquetRows = bouquetRows,
                                onOpenMultiEpg = onOpenMultiEpg
                            )
                        } else if (selectedBouquet != null) {
                            HubServiceGrid(
                                bouquetRef = selectedBouquet.bouquet.reference,
                                services = selectedBouquet.services,
                                onServiceClick = gatedServiceClick,
                                onServiceInfo = onServiceInfo
                            )
                        } else if (movieDir != null) {
                            if (movieLoading && movieDir !in moviesByLocation) {
                                Text(
                                    text = stringResource(R.string.loading),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.testTag("hub_movie_loading")
                                )
                            } else {
                                HubMovieGrid(
                                    dirname = movieDir,
                                    movies = moviesByLocation[movieDir].orEmpty(),
                                    onMovieClick = gatedMovieClick
                                )
                            }
                        } else if (!loading) {
                            HubPlaceholderRow()
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
        try {
            okFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // Overlay not attached yet.
        }
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

/** Persistent Online / Offline / Checking chip. Recheck is focusable when Offline / failed. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubSessionStatus(
    label: String,
    recheckLabel: String,
    onRecheck: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .testTag("hub_session_chip")
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        if (onRecheck != null) {
            Surface(
                onClick = onRecheck,
                modifier = Modifier.testTag("hub_session_recheck"),
                colors = dreamDroidTvCardColors(),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape()
            ) {
                Text(
                    text = recheckLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
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

/** Bouquet cards that open GraphMultiEPG. Public for focused instrumented tests. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubMultiEpgBouquetGrid(
    bouquetRows: List<HubBouquetRow>,
    onOpenMultiEpg: (reference: String, name: String) -> Unit,
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
            .testTag("hub_multiepg_bouquet_grid")
    ) {
        gridItemsIndexed(
            bouquetRows,
            key = { index, row ->
                row.bouquet.reference.ifBlank { "bouquet-$index" }
            }
        ) { index, row ->
            val title = row.bouquet.name.ifBlank { row.bouquet.reference }
            Surface(
                onClick = {
                    onOpenMultiEpg(row.bouquet.reference, title)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hub_multiepg_bouquet_$index"),
                colors = dreamDroidTvCardColors(),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape()
            ) {
                Column {
                    Image(
                        painter = painterResource(R.drawable.ic_multiepg),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(LocalContentColor.current),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
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
    onServiceInfo: ((ServiceNowNext, String?) -> Unit)? = null
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
            .testTag("hub_service_grid")
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
        ) { _, service ->
            HubBouquetServiceItem(
                service = service,
                bouquetRef = bouquetRef,
                onServiceClick = onServiceClick,
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
    contentExpanded: Boolean = false,
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
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(200.dp))
            .then(if (fillWidth) Modifier.height(HubServiceGridCardHeight) else Modifier)
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
        Column(modifier = if (fillWidth) Modifier.fillMaxSize() else Modifier) {
            PiconImage(
                reference = service.serviceReference,
                name = service.serviceName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag("hub_service_picon")
            )
            ImageCardContent(
                title = title,
                contentPrimary = contentPrimary,
                nextStart = nextStart,
                nextTitle = nextTitle,
                contentExpanded = contentExpanded,
                fillWidth = fillWidth,
                imageWidthPx = imageWidthPx,
                modifier = if (fillWidth) Modifier.weight(1f) else Modifier
            )
        }
    }
}

/** One location's movie cards (Phase 3.1c-iv-e). Public for Compose tests. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubMovieRow(
    dirname: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub_movie_row")
    ) {
        items(movies, key = { it.reference + "|" + it.fileName }) { movie ->
            HubMovieCard(
                movie = movie,
                onClick = { onMovieClick(movie) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubMovieGrid(
    dirname: String,
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubPlaceholderRow() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hub_placeholder_row")
    ) {
        items(3) { index ->
            Surface(
                onClick = {},
                modifier = Modifier
                    .width(180.dp)
                    .height(100.dp)
                    .testTag("hub_placeholder_card_$index"),
                colors = dreamDroidTvCardColors(),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                shape = ClickableSurfaceDefaults.shape()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(text = "…", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Kept for iv-b smoke tests / previews. */
@Composable
fun ComposeTvHubStub() {
    val settingsItems = TvComposeHubHost.defaultSettingsKinds().map { kind ->
        kind to stringResource(TvComposeHubHost.settingsTitleRes(kind))
    }
    ComposeTvHubChrome(
        headers = listOf(
            HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
            HubNavHeader(TvComposeHubHost.HEADER_MULTIEPG_ID, "MultiEPG"),
            HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
        ),
        selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
        onHeaderSelected = {},
        settingsItems = settingsItems,
        onSettingsClick = {}
    )
}
