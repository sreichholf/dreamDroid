package net.reichholf.dreamdroid.ui.services

import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Event
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import net.reichholf.dreamdroid.helpers.enigma2.Service
import kotlin.math.roundToInt

/** Window-space top-left of the tapped row — used to anchor View PopupMenus. */
typealias ServiceListTap = (item: ServiceListItem, windowX: Int, windowY: Int) -> Unit

private val EventStartColumnWidth = 45.dp
private val EventEndColumnWidth = 50.dp
/** Thinner than the VLC zap list's 4dp strip so the bar sits on the card's top border. */
private val ProgressBarHeight = 2.dp

@Composable
fun ServiceListScreen(
    items: List<ServiceListItem>,
    onItemClick: ServiceListTap,
    onItemLongClick: ServiceListTap,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        items(items, key = { "${it.index}:${it.reference}" }) { item ->
            ServiceRow(
                item = item,
                onClick = { x, y -> onItemClick(item, x, y) },
                onLongClick = { x, y -> onItemLongClick(item, x, y) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServiceRow(
    item: ServiceListItem,
    onClick: (windowX: Int, windowY: Int) -> Unit,
    onLongClick: (windowX: Int, windowY: Int) -> Unit,
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
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun windowTopLeft(): Pair<Int, Int> {
        val bounds: Rect = coords?.boundsInWindow() ?: return 0 to 0
        return bounds.left.roundToInt() to bounds.top.roundToInt()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onGloballyPositioned { coords = it }
            .combinedClickable(
                onClick = {
                    val (x, y) = windowTopLeft()
                    onClick(x, y)
                },
                onLongClick = {
                    val (x, y) = windowTopLeft()
                    onLongClick(x, y)
                },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (item.kind == ServiceRowKind.CHANNEL && item.progressMax > 0) {
                LinearProgressIndicator(
                    progress = { item.progress.toFloat() / item.progressMax.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ProgressBarHeight),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.kind == ServiceRowKind.CHANNEL &&
                    (item.nowTitle.isNotEmpty() || item.nextTitle.isNotEmpty())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ServicePicon(item)
                        Column(modifier = Modifier.weight(1f)) {
                            if (item.nowTitle.isNotEmpty()) {
                                EventTimeRow(
                                    start = item.nowStart,
                                    title = item.nowTitle,
                                    endValue = item.nowDuration,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (item.nextTitle.isNotEmpty()) {
                                EventTimeRow(
                                    start = item.nextStart,
                                    title = item.nextTitle,
                                    endValue = item.nextDuration,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Start | title | remaining-or-duration, with fixed start/end columns so now and next
 * stack with dedicated fields aligned underneath each other (same as the VLC zap list).
 */
@Composable
private fun EventTimeRow(
    start: String,
    title: String,
    endValue: String,
    style: TextStyle,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = start,
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.width(EventStartColumnWidth),
        )
        Text(
            text = title,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        Text(
            text = endValue,
            style = style,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(EventEndColumnWidth),
        )
    }
}

@Composable
private fun ServicePicon(item: ServiceListItem) {
    val context = LocalContext.current
    val piconsEnabled = PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, DreamDroid.isTV(context))
    if (!piconsEnabled) {
        return
    }
    AndroidView(
        factory = { ctx -> ImageView(ctx) },
        modifier = Modifier
            .padding(end = 8.dp)
            .size(width = 48.dp, height = 30.dp),
        update = { view ->
            val map = ExtendedHashMap()
            map.put(Service.KEY_REFERENCE, item.reference)
            map.put(Event.KEY_SERVICE_REFERENCE, item.reference)
            map.put(Event.KEY_SERVICE_NAME, item.name)
            Picon.setPiconForView(context, view, map, Statics.TAG_PICON)
        },
    )
}
