package net.reichholf.dreamdroid.ui.services

import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import net.reichholf.dreamdroid.helpers.enigma2.Service

@Composable
fun ServiceListScreen(
    items: List<ServiceListItem>,
    onItemClick: (ServiceListItem) -> Unit,
    onItemLongClick: (ServiceListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        items(items, key = { "${it.index}:${it.reference}" }) { item ->
            ServiceRow(
                item = item,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServiceRow(
    item: ServiceListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    if (item.kind == ServiceRowKind.MARKER) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.kind == ServiceRowKind.CHANNEL) {
                    ServicePicon(item)
                }
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (item.kind == ServiceRowKind.CHANNEL) {
                if (item.nowTitle.isNotEmpty()) {
                    Text(
                        text = "${item.nowStart}  ${item.nowTitle}  ${item.nowDuration}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (item.progressMax > 0) {
                    LinearProgressIndicator(
                        progress = item.progress.toFloat() / item.progressMax.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
                if (item.nextTitle.isNotEmpty()) {
                    Text(
                        text = "${item.nextStart}  ${item.nextTitle}  ${item.nextDuration}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServicePicon(item: ServiceListItem) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx -> ImageView(ctx) },
        modifier = Modifier.size(48.dp),
        update = { view ->
            val map = ExtendedHashMap()
            map.put(Service.KEY_REFERENCE, item.reference)
            map.put(Event.KEY_SERVICE_REFERENCE, item.reference)
            map.put(Event.KEY_SERVICE_NAME, item.name)
            Picon.setPiconForView(context, view, map, Statics.TAG_PICON)
        },
    )
}
