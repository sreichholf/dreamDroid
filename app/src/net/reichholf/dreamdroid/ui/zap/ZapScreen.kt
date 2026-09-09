package net.reichholf.dreamdroid.ui.zap

import android.util.Log
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.squareup.picasso.Callback
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Picon

private const val TAG = "ZapScreen"

@Composable
fun ZapScreen(
    items: List<Service>,
    onItemClick: (Service) -> Unit,
    onItemLongClick: (Service) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    scrollEpoch: Int = 0,
    emptyMessage: String? = null,
) {
    LaunchedEffect(scrollEpoch) {
        if (scrollEpoch > 0) {
            gridState.scrollToItem(0)
        }
    }

    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (emptyMessage != null) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        return
    }

    val itemHeight = dimensionResource(R.dimen.zap_grid_item_height)
    val minCellWidth = with(LocalDensity.current) {
        (itemHeight.toPx() / 9f * 16f).toDp()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellWidth),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { "${it.reference}:${it.name}" }) { service ->
            ZapServiceCard(
                service = service,
                onClick = { onItemClick(service) },
                onLongClick = { onItemLongClick(service) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZapServiceCard(
    service: Service,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var piconLoaded by remember(service.reference, service.name) { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                Picon.setPiconForView(
                    context,
                    view,
                    service.reference,
                    service.name,
                    Statics.TAG_PICON,
                    object : Callback {
                        override fun onSuccess() {
                            piconLoaded = true
                        }

                        override fun onError(e: Exception?) {
                            Log.w(TAG, "Error loading picon for ${service.name}")
                            piconLoaded = false
                        }
                    },
                )
            },
        )
        if (!piconLoaded) {
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}
