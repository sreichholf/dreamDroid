package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
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
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.loadEpgNowNext
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.tv.activities.PreferenceActivity
import net.reichholf.dreamdroid.tv.fragment.RootBrowseFragment
import net.reichholf.dreamdroid.tv.view.ImageCardContent
import net.reichholf.dreamdroid.ui.services.serviceNowNextToExtendedHashMap

/**
 * Phase 3.1c-iv Compose TV hub host.
 * - **iv-b:** debug-only switch (default Leanback).
 * - **iv-c:** [NavigationDrawer] side headers + row focus chrome; settings
 *   Reload / Preferences / Profile.
 * - **iv-d:** bouquet headers + service/now-next rows with picon cards.
 *   Movie rows land in iv-e. Stream Intent edge unchanged.
 */
object TvComposeHubHost {
    const val PREFS_KEY_COMPOSE_TV_HUB: String = "compose_tv_hub_debug"
    const val HEADER_SETTINGS_ID: String = "settings"
    const val HEADER_PLACEHOLDER_ID: String = "placeholder"

    @JvmStatic
    fun useComposeHub(context: Context): Boolean {
        if (!BuildConfig.DEBUG) {
            return false
        }
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREFS_KEY_COMPOSE_TV_HUB, false)
    }

    @JvmStatic
    fun install(activity: ComponentActivity) {
        activity.setContent {
            ComposeTvHubApp(activity = activity)
        }
    }
}

data class HubNavHeader(
    val id: String,
    val title: String,
)

data class HubBouquetRow(
    val bouquet: Service,
    val services: List<ServiceNowNext>,
)

@Composable
fun ComposeTvHubApp(activity: ComponentActivity) {
    val settingsTitle = stringResource(R.string.preferences)
    val placeholderTitle = stringResource(R.string.services)
    var reloadToken by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var bouquetRows by remember { mutableStateOf<List<HubBouquetRow>>(emptyList()) }
    var selectedHeaderId by remember { mutableStateOf(TvComposeHubHost.HEADER_SETTINGS_ID) }

    LaunchedEffect(reloadToken) {
        loading = true
        errorText = null
        val result = loadComposeHubBouquets(activity)
        loading = false
        errorText = result.errorText
        bouquetRows = result.rows
        if (selectedHeaderId != TvComposeHubHost.HEADER_SETTINGS_ID &&
            bouquetRows.none { it.bouquet.reference == selectedHeaderId }
        ) {
            selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID
        }
    }

    val headers = remember(settingsTitle, placeholderTitle, bouquetRows) {
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
        }
    }
    val settingsItems = listOf(
        BrowseItem.Kind.Reload to stringResource(R.string.reload),
        BrowseItem.Kind.Preferences to stringResource(R.string.settings),
        BrowseItem.Kind.Profile to stringResource(R.string.profile),
    )

    ComposeTvHubChrome(
        headers = headers,
        selectedHeaderId = selectedHeaderId,
        onHeaderSelected = { selectedHeaderId = it },
        settingsItems = settingsItems,
        onSettingsClick = { kind ->
            when (kind) {
                BrowseItem.Kind.Reload -> reloadToken++
                else -> handleSettingsAction(activity, kind)
            }
        },
        bouquetRows = bouquetRows,
        loading = loading,
        errorText = errorText,
        onServiceClick = { service, bouquetRef ->
            openServiceStream(activity, service, bouquetRef)
        },
    )
}

private data class HubLoadResult(
    val rows: List<HubBouquetRow>,
    val errorText: String?,
)

/**
 * Prefetch locations/tags (Leanback parity), then bouquets + now/next per bouquet.
 * A failed bouquet is skipped so other rows can still appear.
 */
private suspend fun loadComposeHubBouquets(context: Context): HubLoadResult {
    withContext(Dispatchers.IO) {
        val http = SimpleHttpClient.getInstance()
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
    val bouquetParams = listOf(NameValuePair("bRef", RootBrowseFragment.BOUQUETS_TV))
    val bouquetResult = loadServiceList(context, bouquetParams)
    if (!bouquetResult.success) {
        return HubLoadResult(emptyList(), bouquetResult.errorText)
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
    return HubLoadResult(rows, lastError)
}

private fun openServiceStream(
    activity: ComponentActivity,
    service: ServiceNowNext,
    bouquetRef: String?,
) {
    val map = serviceNowNextToExtendedHashMap(service)
    val title = service.now?.title?.takeIf { it.isNotEmpty() } ?: service.serviceName
    activity.startActivity(
        IntentFactory.getStreamServiceIntent(
            activity,
            service.serviceReference,
            title,
            bouquetRef,
            map,
        ),
    )
}

private fun handleSettingsAction(activity: ComponentActivity, kind: BrowseItem.Kind) {
    when (kind) {
        BrowseItem.Kind.Reload -> {
            Toast.makeText(activity, R.string.reload, Toast.LENGTH_SHORT).show()
        }
        BrowseItem.Kind.Preferences -> {
            activity.startActivity(
                Intent(activity, PreferenceActivity::class.java).putExtra(
                    PreferenceActivity.KEY_PREFS_TYPE,
                    PreferenceActivity.PREFS_TYPE_GENERIC,
                ),
            )
        }
        BrowseItem.Kind.Profile -> {
            activity.startActivity(
                Intent(activity, PreferenceActivity::class.java).putExtra(
                    PreferenceActivity.KEY_PREFS_TYPE,
                    PreferenceActivity.PREFS_TYPE_PROFILE,
                ),
            )
        }
    }
}

/** Side headers ([NavigationDrawer]) + row list focus chrome (Phase 3.1c-iv-c/d). */
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
    loading: Boolean = false,
    errorText: String? = null,
    onServiceClick: (ServiceNowNext, String?) -> Unit = { _, _ -> },
) {
    MaterialTheme {
        NavigationDrawer(
            modifier = modifier
                .fillMaxSize()
                .testTag("compose_tv_hub_chrome"),
            drawerContent = {
                Column(
                    modifier = Modifier.padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    headers.forEach { header ->
                        NavigationDrawerItem(
                            selected = header.id == selectedHeaderId,
                            onClick = { onHeaderSelected(header.id) },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(24.dp),
                                )
                            },
                            modifier = Modifier.testTag("hub_header_${header.id}"),
                        ) {
                            Text(text = header.title)
                        }
                    }
                }
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag("compose_tv_hub_rows"),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                item {
                    val title = headers.firstOrNull { it.id == selectedHeaderId }?.title.orEmpty()
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (loading) {
                    item {
                        Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("hub_loading"),
                        )
                    }
                }
                if (errorText != null && !loading) {
                    item {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("hub_error"),
                        )
                    }
                }
                if (selectedHeaderId == TvComposeHubHost.HEADER_SETTINGS_ID) {
                    item {
                        HubSettingsRow(
                            settingsItems = settingsItems,
                            onSettingsClick = onSettingsClick,
                        )
                    }
                } else {
                    val selectedBouquet = bouquetRows.firstOrNull {
                        it.bouquet.reference == selectedHeaderId
                    }
                    if (selectedBouquet != null) {
                        item {
                            HubServiceRow(
                                bouquetRef = selectedBouquet.bouquet.reference,
                                services = selectedBouquet.services,
                                onServiceClick = onServiceClick,
                            )
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

/** Settings action row — public for focused instrumented tests without drawer focus noise. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HubSettingsRow(
    settingsItems: List<Pair<BrowseItem.Kind, String>>,
    onSettingsClick: (BrowseItem.Kind) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub_settings_row"),
    ) {
        items(settingsItems, key = { it.first.name }) { (kind, title) ->
            Surface(
                onClick = { onSettingsClick(kind) },
                modifier = Modifier
                    .width(180.dp)
                    .height(100.dp)
                    .testTag("hub_settings_${kind.name.lowercase()}"),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
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
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub_service_row"),
    ) {
        items(services, key = { it.serviceReference }) { service ->
            HubServiceCard(
                service = service,
                onClick = { onServiceClick(service, bouquetRef) },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HubServiceCard(
    service: ServiceNowNext,
    onClick: () -> Unit,
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
        modifier = Modifier
            .width(200.dp)
            .testTag("hub_service_card"),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }
                },
                update = { imageView ->
                    Picon.setPiconForView(
                        imageView.context,
                        imageView,
                        service.serviceReference,
                        service.serviceName,
                        "compose_tv_hub",
                        null,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .testTag("hub_service_picon"),
            )
            ImageCardContent(
                title = title,
                contentPrimary = contentPrimary,
                nextStart = nextStart,
                nextTitle = nextTitle,
                contentExpanded = false,
                imageWidthPx = imageWidthPx,
            )
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
            .testTag("hub_placeholder_row"),
    ) {
        items(3) { index ->
            Surface(
                onClick = {},
                modifier = Modifier
                    .width(180.dp)
                    .height(100.dp)
                    .testTag("hub_placeholder_card_$index"),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart,
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
    ComposeTvHubChrome(
        headers = listOf(
            HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
            HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services"),
        ),
        selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
        onHeaderSelected = {},
        settingsItems = listOf(
            BrowseItem.Kind.Reload to "Reload",
            BrowseItem.Kind.Preferences to "Settings",
            BrowseItem.Kind.Profile to "Profile",
        ),
        onSettingsClick = {},
    )
}
