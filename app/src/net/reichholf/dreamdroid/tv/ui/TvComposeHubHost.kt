package net.reichholf.dreamdroid.tv.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.loadEpgNowNext
import net.reichholf.dreamdroid.enigma.loadMovieList
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.tv.activities.MainActivity
import net.reichholf.dreamdroid.tv.activities.MultiEpgActivity
import net.reichholf.dreamdroid.tv.activities.PreferenceActivity
import net.reichholf.dreamdroid.tv.view.FittedEllipsisText
import net.reichholf.dreamdroid.tv.view.ImageCardContent
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache
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
    const val HEADER_SETTINGS_ID: String = "settings"
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

    fun preferenceTypeForKind(kind: BrowseItem.Kind): String? = when (kind) {
        BrowseItem.Kind.Preferences -> PreferenceActivity.PREFS_TYPE_GENERIC

        BrowseItem.Kind.Profile -> PreferenceActivity.PREFS_TYPE_PROFILE

        BrowseItem.Kind.Reload,
        BrowseItem.Kind.MultiEpg -> null
    }

    fun defaultSettingsKinds(): List<BrowseItem.Kind> = listOf(
        BrowseItem.Kind.Reload,
        BrowseItem.Kind.Preferences,
        BrowseItem.Kind.Profile,
        BrowseItem.Kind.MultiEpg
    )

    fun settingsTitleRes(kind: BrowseItem.Kind): Int = when (kind) {
        BrowseItem.Kind.Reload -> R.string.reload
        BrowseItem.Kind.Preferences -> R.string.settings
        BrowseItem.Kind.Profile -> R.string.profile
        BrowseItem.Kind.MultiEpg -> R.string.multiepg
    }

    /** Same badges the Leanback Live TV settings cards used. */
    fun settingsBadgeRes(kind: BrowseItem.Kind): Int = when (kind) {
        BrowseItem.Kind.Reload -> R.drawable.ic_badge_reload
        BrowseItem.Kind.Preferences -> R.drawable.ic_badge_settings
        BrowseItem.Kind.Profile -> R.drawable.ic_badge_profiles
        BrowseItem.Kind.MultiEpg -> R.drawable.ic_menu_tv
    }

    fun preferenceIntent(context: Context, kind: BrowseItem.Kind): Intent? {
        val type = preferenceTypeForKind(kind) ?: return null
        return Intent(context, PreferenceActivity::class.java).putExtra(
            PreferenceActivity.KEY_PREFS_TYPE,
            type
        )
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

    fun applyPreferenceActivityResult(resultCode: Int, onReload: () -> Unit) {
        if (resultCode == Activity.RESULT_OK) {
            onReload()
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
            ComposeTvHubApp(
                activity = activity,
                onRecheckProfile = { host?.recheckProfile() }
            )
        }
    }
}

data class HubNavHeader(val id: String, val title: String)

data class HubBouquetRow(val bouquet: Service, val services: List<ServiceNowNext>)

@Composable
fun ComposeTvHubApp(
    activity: ComponentActivity,
    onRecheckProfile: () -> Unit = { (activity as? MainActivity)?.recheckProfile() }
) {
    val context = LocalContext.current
    val status by SessionConnectionHolder.shared.status.collectAsState()
    val profile = DreamDroid.getCurrentProfile()
    val hasCache = remember(status, profile.id) {
        hasUseDrivenCache(profile, context)
    }
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
    val placeholderTitle = stringResource(R.string.services)
    var reloadToken by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var bouquetRows by remember { mutableStateOf<List<HubBouquetRow>>(emptyList()) }
    var movieLocations by remember { mutableStateOf<List<String>>(emptyList()) }
    var moviesByLocation by remember { mutableStateOf<Map<String, List<Movie>>>(emptyMap()) }
    var movieLoading by remember { mutableStateOf(false) }
    var movieError by remember { mutableStateOf<String?>(null) }
    var selectedHeaderId by remember { mutableStateOf(TvComposeHubHost.HEADER_SETTINGS_ID) }
    val preferenceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        TvComposeHubHost.applyPreferenceActivityResult(result.resultCode) {
            reloadToken++
        }
    }

    LaunchedEffect(reloadToken) {
        loading = true
        errorText = null
        movieError = null
        moviesByLocation = emptyMap()
        val result = loadComposeHubBouquets(activity)
        loading = false
        errorText = result.errorText
        bouquetRows = result.rows
        movieLocations = result.locations
        val stillValid = selectedHeaderId == TvComposeHubHost.HEADER_SETTINGS_ID ||
            bouquetRows.any { it.bouquet.reference == selectedHeaderId } ||
            TvComposeHubHost.movieDirnameFromHeader(selectedHeaderId) in movieLocations
        if (!stillValid) {
            selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID
        }
    }

    // Leanback parity: load movies for a location only when its header is selected.
    LaunchedEffect(selectedHeaderId, reloadToken) {
        val dirname =
            TvComposeHubHost.movieDirnameFromHeader(selectedHeaderId) ?: return@LaunchedEffect
        if (dirname in moviesByLocation) {
            return@LaunchedEffect
        }
        movieLoading = true
        movieError = null
        val result = loadMovieList(activity, listOf(NameValuePair("dirname", dirname)))
        movieLoading = false
        if (result.success) {
            moviesByLocation = moviesByLocation + (dirname to result.movies)
        } else {
            movieError = result.errorText
        }
    }

    val headers = remember(settingsTitle, placeholderTitle, bouquetRows, movieLocations) {
        buildList {
            add(HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, settingsTitle))
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
    val openProfiles = {
        val intent = TvComposeHubHost.preferenceIntent(activity, BrowseItem.Kind.Profile)
        if (intent != null) {
            preferenceLauncher.launch(intent)
        }
    }

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

    ComposeTvHubChrome(
        headers = headers,
        selectedHeaderId = selectedHeaderId,
        onHeaderSelected = { selectedHeaderId = it },
        settingsItems = settingsItems,
        onSettingsClick = { kind ->
            when (kind) {
                BrowseItem.Kind.Reload -> reloadToken++

                BrowseItem.Kind.Preferences, BrowseItem.Kind.Profile -> {
                    val intent = TvComposeHubHost.preferenceIntent(activity, kind)
                    if (intent != null) {
                        preferenceLauncher.launch(intent)
                    }
                }

                BrowseItem.Kind.MultiEpg -> {
                    activity.startActivity(Intent(activity, MultiEpgActivity::class.java))
                }
            }
        },
        bouquetRows = bouquetRows,
        moviesByLocation = moviesByLocation,
        loading = loading,
        movieLoading = movieLoading,
        errorText = errorText ?: movieError,
        onServiceClick = { service, bouquetRef ->
            openServiceStream(activity, service, bouquetRef)
        },
        onMovieClick = { movie ->
            openMovieStream(activity, movie)
        },
        sessionChipLabel = stringResource(status.chipLabelRes()),
        onSessionRecheck = if (shouldShowTvSessionRecheck(status)) {
            onRecheckProfile
        } else {
            null
        }
    )
}

private data class HubLoadResult(
    val rows: List<HubBouquetRow>,
    val locations: List<String>,
    val errorText: String?
)

/**
 * Prefetch locations/tags (Leanback parity), then bouquets + now/next per bouquet.
 * A failed bouquet is skipped so other rows can still appear.
 */
private suspend fun loadComposeHubBouquets(context: Context): HubLoadResult {
    withContext(Dispatchers.IO) {
        val http = EnigmaHttp()
        if (DreamDroid.getLocations().size <= 1) {
            if (!DreamDroid.loadLocations(http)) {
                Log.e(DreamDroid.LOG_TAG, "ERROR loading locations")
            }
        }
        if (DreamDroid.getTags().size <= 1) {
            if (!DreamDroid.loadTags(http)) {
                Log.e(DreamDroid.LOG_TAG, "ERROR loading tags")
            }
        }
    }
    val locations = DreamDroid.getLocations().toList()
    val bouquetParams = listOf(NameValuePair("bRef", TvComposeHubHost.BOUQUETS_TV))
    val bouquetResult = loadServiceList(context, bouquetParams)
    if (!bouquetResult.success) {
        return HubLoadResult(emptyList(), locations, bouquetResult.errorText)
    }
    val rows = ArrayList<HubBouquetRow>()
    var lastError: String? = null
    for (bouquet in bouquetResult.services) {
        val ref = bouquet.reference
        if (ref.isBlank()) continue
        val epg = loadEpgNowNext(context, listOf(NameValuePair("bRef", ref)))
        if (!epg.success) {
            lastError = epg.errorText
            continue
        }
        rows.add(HubBouquetRow(bouquet = bouquet, services = epg.rows))
    }
    return HubLoadResult(rows, locations, lastError)
}

private fun openServiceStream(
    activity: ComponentActivity,
    service: ServiceNowNext,
    bouquetRef: String?
) {
    TvComposeHubHost.startStreamIntent(
        activity,
        TvComposeHubHost.streamServiceIntent(activity, service, bouquetRef)
    )
}

private fun openMovieStream(activity: ComponentActivity, movie: Movie) {
    TvComposeHubHost.startStreamIntent(
        activity,
        TvComposeHubHost.streamMovieIntent(activity, movie)
    )
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
    onServiceClick: (ServiceNowNext, String?) -> Unit = { _, _ -> },
    onMovieClick: (Movie) -> Unit = {},
    sessionChipLabel: String? = null,
    onSessionRecheck: (() -> Unit)? = null,
    sessionRecheckLabel: String? = null
) {
    DreamDroidTvTheme {
        NavigationDrawer(
            modifier = modifier
                .fillMaxSize()
                .testTag("compose_tv_hub_chrome"),
            drawerContent = {
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    headers.forEach { header ->
                        NavigationDrawerItem(
                            selected = header.id == selectedHeaderId,
                            onClick = { onHeaderSelected(header.id) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(24.dp)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag("compose_tv_hub_rows"),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                item {
                    val title = headers.firstOrNull { it.id == selectedHeaderId }?.title.orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
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
                if (loading) {
                    item {
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("hub_loading")
                        )
                    }
                }
                if (
                    TvComposeHubHost.shouldShowBrowseError(
                        selectedHeaderId,
                        loading,
                        errorText
                    )
                ) {
                    item {
                        Text(
                            text = errorText.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("hub_error")
                        )
                    }
                }
                if (selectedHeaderId == TvComposeHubHost.HEADER_SETTINGS_ID) {
                    item {
                        HubSettingsRow(
                            settingsItems = settingsItems,
                            onSettingsClick = onSettingsClick
                        )
                    }
                } else {
                    val selectedBouquet = bouquetRows.firstOrNull {
                        it.bouquet.reference == selectedHeaderId
                    }
                    val movieDir = TvComposeHubHost.movieDirnameFromHeader(selectedHeaderId)
                    if (selectedBouquet != null) {
                        item {
                            HubServiceRow(
                                bouquetRef = selectedBouquet.bouquet.reference,
                                services = selectedBouquet.services,
                                onServiceClick = onServiceClick
                            )
                        }
                    } else if (movieDir != null) {
                        if (movieLoading && movieDir !in moviesByLocation) {
                            item {
                                Text(
                                    text = stringResource(R.string.loading),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.testTag("hub_movie_loading")
                                )
                            }
                        } else {
                            item {
                                HubMovieRow(
                                    dirname = movieDir,
                                    movies = moviesByLocation[movieDir].orEmpty(),
                                    onMovieClick = onMovieClick
                                )
                            }
                        }
                    } else if (!loading) {
                        item {
                            HubPlaceholderRow()
                        }
                    }
                }
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
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
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
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
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
    onScrollInProgress: ((Boolean) -> Unit)? = null
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
            HubServiceCard(
                service = service,
                onClick = { onServiceClick(service, bouquetRef) },
                modifier = if (index == 0 && firstItemFocusRequester != null) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                },
                onFocused = onUserInteraction
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubServiceCard(
    service: ServiceNowNext,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null
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
            .width(200.dp)
            .testTag("hub_service_card")
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused?.invoke()
                }
            },
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Column {
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
                contentExpanded = false,
                imageWidthPx = imageWidthPx
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
private fun HubMovieCard(movie: Movie, onClick: () -> Unit) {
    val descriptionEx = movie.descriptionExtended.replace("\\n", "\n")
    val content = if (descriptionEx.isNotEmpty()) descriptionEx else movie.description
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(160.dp)
            .testTag("hub_movie_card"),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
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
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
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
            HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
        ),
        selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
        onHeaderSelected = {},
        settingsItems = settingsItems,
        onSettingsClick = {}
    )
}
