package net.reichholf.dreamdroid.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.loadBouquetList
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors
import net.reichholf.dreamdroid.ui.zap.ZapListMapper

/**
 * D-pad timer service picker: bouquet list, then channels. Back on channels returns
 * to bouquets; Back on bouquets dismisses. TV then radio, markers skipped.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTimerServicePick(
    onPicked: (Service) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bouquet by remember { mutableStateOf<Service?>(null) }
    var bouquets by remember { mutableStateOf<List<Service>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Service>>(emptyList()) }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    val firstRowFocus = remember { FocusRequester() }
    val rows = if (bouquet == null) bouquets else channels
    val loadingText = stringResource(R.string.loading)
    val emptyText = stringResource(R.string.no_list_item)

    BackHandler {
        if (bouquet != null) {
            bouquet = null
            channels = emptyList()
            emptyMessage = null
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(bouquet) {
        val selected = bouquet
        if (selected == null) {
            if (bouquets.isNotEmpty()) {
                emptyMessage = null
                return@LaunchedEffect
            }
            emptyMessage = loadingText
            val result = loadBouquetList(context.applicationContext)
            if (!result.success) {
                val profileId = DreamDroid.getCurrentProfile().id
                val cached = if (profileId != null) {
                    val dao = AppDatabase.roster(context)
                    val cachedRows = ArrayList(
                        UserBouquetCache.loadTabStripServices(
                            dao,
                            profileId,
                            UserBouquetCache.KIND_TV
                        )
                    )
                    cachedRows.addAll(
                        UserBouquetCache.loadTabStripServices(
                            dao,
                            profileId,
                            UserBouquetCache.KIND_RADIO
                        )
                    )
                    cachedRows
                } else {
                    emptyList()
                }
                if (cached.isNotEmpty()) {
                    bouquets = cached.withoutMarkers()
                    emptyMessage = if (bouquets.isEmpty()) emptyText else null
                    return@LaunchedEffect
                }
                bouquets = emptyList()
                emptyMessage = result.errorText ?: emptyText
                return@LaunchedEffect
            }
            val combined = ArrayList(result.bouquets.tv)
            combined.addAll(result.bouquets.radio)
            bouquets = combined.withoutMarkers()
            emptyMessage = if (bouquets.isEmpty()) emptyText else null
            return@LaunchedEffect
        }
        emptyMessage = loadingText
        channels = emptyList()
        val result = loadServiceList(
            context.applicationContext,
            listOf(NameValuePair("sRef", selected.reference))
        )
        if (!result.success) {
            val profileId = DreamDroid.getCurrentProfile().id
            val cached = if (profileId != null) {
                UserBouquetCache.loadRosterServices(
                    AppDatabase.roster(context),
                    profileId,
                    selected.reference
                )
            } else {
                null
            }
            if (cached != null) {
                channels = ZapListMapper.rowsFrom(cached)
                emptyMessage = if (channels.isEmpty()) emptyText else null
                return@LaunchedEffect
            }
            channels = emptyList()
            emptyMessage = result.errorText ?: emptyText
            return@LaunchedEffect
        }
        channels = ZapListMapper.rowsFrom(result.services)
        emptyMessage = if (channels.isEmpty()) emptyText else null
    }

    LaunchedEffect(rows) {
        if (rows.isEmpty()) {
            return@LaunchedEffect
        }
        try {
            firstRowFocus.requestFocus()
        } catch (_: IllegalStateException) {
            // Overlay not attached yet.
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneMaterialTheme.colorScheme.background)
            .focusGroup()
            .testTag("tv_timer_service_pick")
    ) {
        if (rows.isEmpty()) {
            Text(
                text = emptyMessage ?: loadingText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(rows, key = { index, service ->
                    "${service.reference}-$index"
                }) { index, service ->
                    Surface(
                        onClick = {
                            if (ServiceKeys.isMarker(service.reference)) {
                                return@Surface
                            }
                            if (bouquet == null) {
                                bouquet = service
                            } else {
                                onPicked(service)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(firstRowFocus)
                                } else {
                                    Modifier
                                }
                            ),
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
}

private fun List<Service>.withoutMarkers(): List<Service> = filter { service ->
    !ServiceKeys.isMarker(service.reference)
}
