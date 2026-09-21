package net.reichholf.dreamdroid.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.room.UserBouquetCache
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

const val OVERLAY_BOUQUET_BAR_TAG = "overlay_bouquet_bar"

/**
 * User-bouquet names for the stream overlay, TV first then radio.
 * Provider roots and the aggregate index are dropped.
 */
fun overlayBouquets(tv: List<Service>, radio: List<Service>, excluded: Set<String>): List<Service> {
    val tvTabs = UserBouquetCache.userBouquetTabs(tv, excluded)
    val radioTabs = UserBouquetCache.userBouquetTabs(radio, excluded)
    return (tvTabs + radioTabs).distinctBy { it.reference }
}

@Composable
fun OverlayBouquetBar(
    bouquets: List<Service>,
    selectedRef: String?,
    onBouquetClick: (Service) -> Unit,
    modifier: Modifier = Modifier,
    onScrollInProgress: ((Boolean) -> Unit)? = null
) {
    if (bouquets.isEmpty()) {
        return
    }
    val listState = rememberLazyListState()
    val barLabel = stringResource(R.string.bouquet_overview)
    LaunchedEffect(bouquets, selectedRef) {
        val index = bouquets.indexOfFirst { it.reference == selectedRef }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }
    LaunchedEffect(listState, onScrollInProgress) {
        if (onScrollInProgress == null) {
            return@LaunchedEffect
        }
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            onScrollInProgress(scrolling)
        }
    }
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .testTag(OVERLAY_BOUQUET_BAR_TAG)
            .semantics { contentDescription = barLabel },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        items(bouquets, key = { it.reference }) { bouquet ->
            val selected = bouquet.reference == selectedRef
            FilterChip(
                selected = selected,
                onClick = { onBouquetClick(bouquet) },
                label = {
                    Text(
                        text = bouquet.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}

fun ComposeView.bindOverlayBouquetBar(
    state: VideoOverlayUiState,
    onBouquetClick: (Service) -> Unit,
    onUserInteraction: () -> Unit,
    onScrollInProgress: (Boolean) -> Unit
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme(forceDark = true) {
            OverlayBouquetBar(
                bouquets = state.bouquets,
                selectedRef = state.selectedBouquetRef,
                onBouquetClick = { bouquet ->
                    onUserInteraction()
                    onBouquetClick(bouquet)
                },
                onScrollInProgress = onScrollInProgress
            )
        }
    }
}
