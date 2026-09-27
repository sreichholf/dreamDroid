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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.ui.pick.TimerServicePickViewModel
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors

/**
 * The picker has no nav entry of its own, so its ViewModel lives on the activity. The key
 * carries the profile because a TV profile switch does not recreate the activity.
 */
internal fun tvTimerServicePickKey(): String =
    "tv-timer-service-pick:${ProfileRepository.get().current.value?.id}"

/**
 * D-pad timer service picker: bouquet list, then channels. Back on channels returns
 * to bouquets; Back on bouquets dismisses. TV then radio, markers skipped.
 * Reopening the picker shows the bouquet and list it was left on.
 */
@Composable
fun TvTimerServicePick(
    onPicked: (Service) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimerServicePickViewModel = viewModel(key = tvTimerServicePickKey())
) {
    val session = viewModel.session
    val items = session.listState.items
    val rows = if (session.bouquetRef.isEmpty()) items.withoutMarkers() else items.toList()

    BackHandler {
        if (session.bouquetRef.isNotEmpty()) {
            session.showBouquetList()
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(viewModel) {
        // The activity-scoped list outlives a failed load; retry it on every open.
        if (session.listState.items.isEmpty()) {
            session.reload()
        }
    }

    TvTimerServicePickScreen(
        rows = rows,
        emptyMessage = session.emptyMessage,
        onRowClick = { service ->
            session.onRowClick(service)?.let(onPicked)
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTimerServicePickScreen(
    rows: List<Service>,
    emptyMessage: String?,
    onRowClick: (Service) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstRowFocus = remember { FocusRequester() }
    val loadingText = stringResource(R.string.loading)

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
                        onClick = { onRowClick(service) },
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
