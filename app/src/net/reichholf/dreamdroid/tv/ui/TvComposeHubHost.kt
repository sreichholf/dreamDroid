package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.tv.activities.PreferenceActivity

/**
 * Phase 3.1c-iv Compose TV hub host.
 * - **iv-b:** debug-only switch (default Leanback).
 * - **iv-c:** [NavigationDrawer] side headers + row focus chrome; settings
 *   Reload / Preferences / Profile. Service / movie rows land in iv-d / iv-e.
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

@Composable
fun ComposeTvHubApp(activity: ComponentActivity) {
    val settingsTitle = stringResource(R.string.preferences)
    val placeholderTitle = stringResource(R.string.services)
    val headers = remember(settingsTitle, placeholderTitle) {
        listOf(
            HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, settingsTitle),
            // Focus chrome stand-in until iv-d loads real bouquet headers.
            HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, placeholderTitle),
        )
    }
    var selectedHeaderId by remember { mutableStateOf(TvComposeHubHost.HEADER_SETTINGS_ID) }
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
        onSettingsClick = { kind -> handleSettingsAction(activity, kind) },
    )
}

private fun handleSettingsAction(activity: ComponentActivity, kind: BrowseItem.Kind) {
    when (kind) {
        BrowseItem.Kind.Reload -> {
            // Real bouquet/movie reload arrives with iv-d/e; acknowledge for chrome parity.
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

/** Side headers ([NavigationDrawer]) + row list focus chrome (Phase 3.1c-iv-c). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ComposeTvHubChrome(
    headers: List<HubNavHeader>,
    selectedHeaderId: String,
    onHeaderSelected: (String) -> Unit,
    settingsItems: List<Pair<BrowseItem.Kind, String>>,
    onSettingsClick: (BrowseItem.Kind) -> Unit,
    modifier: Modifier = Modifier,
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
                if (selectedHeaderId == TvComposeHubHost.HEADER_SETTINGS_ID) {
                    item {
                        HubSettingsRow(
                            settingsItems = settingsItems,
                            onSettingsClick = onSettingsClick,
                        )
                    }
                } else {
                    item {
                        HubPlaceholderRow()
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
