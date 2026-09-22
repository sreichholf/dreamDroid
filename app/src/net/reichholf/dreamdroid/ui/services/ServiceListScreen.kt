package net.reichholf.dreamdroid.ui.services

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.ui.compose.ListRowHorizontalInset
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.listRowAnchoredClickable
import net.reichholf.dreamdroid.ui.compose.listRowItemColors

/** Window-space top-left of the tapped row — used to anchor View PopupMenus. */
typealias ServiceListTap = (item: ServiceListItem, windowX: Int, windowY: Int) -> Unit

private val EventStartColumnWidth = 45.dp
private val EventEndColumnWidth = 50.dp

/** Slightly taller than the VLC zap list's 4dp strip so the top-edge progress reads clearly. */
private val ProgressBarHeight = 6.dp

const val SERVICE_LIST_PROGRESS_TAG = "service_list_progress"

@Composable
fun ServiceListScreen(
    items: List<ServiceListItem>,
    onItemClick: ServiceListTap,
    onItemLongClick: ServiceListTap,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(items, key = { "${it.index}:${it.reference}" }) { item ->
            ServiceRow(
                item = item,
                onClick = { x, y -> onItemClick(item, x, y) },
                onLongClick = { x, y -> onItemLongClick(item, x, y) }
            )
        }
    }
}

@Composable
internal fun ServiceRow(
    item: ServiceListItem,
    onClick: (windowX: Int, windowY: Int) -> Unit,
    onLongClick: (windowX: Int, windowY: Int) -> Unit
) {
    if (item.kind == ServiceRowKind.MARKER) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ListRowHorizontalInset + 16.dp, vertical = 8.dp)
                .wrapContentHeight(Alignment.CenterVertically)
        )
        return
    }
    val hasNowNext =
        item.kind == ServiceRowKind.CHANNEL &&
            (item.nowTitle.isNotEmpty() || item.nextTitle.isNotEmpty())
    ListRowSurface(
        modifier = Modifier.listRowAnchoredClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        if (item.kind == ServiceRowKind.CHANNEL && item.progressMax > 0) {
            // Card-top strip: opt out of M3 track, gap, and trailing stop indicator.
            LinearProgressIndicator(
                progress = { item.progress.toFloat() / item.progressMax.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressBarHeight)
                    .testTag(SERVICE_LIST_PROGRESS_TAG),
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        ListItem(
            headlineContent = {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent =
                if (hasNowNext) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ServicePicon(item)
                            Column(modifier = Modifier.weight(1f)) {
                                if (item.nowTitle.isNotEmpty()) {
                                    EventTimeRow(
                                        start = item.nowStart,
                                        title = item.nowTitle,
                                        endValue = item.nowDuration,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (item.nextTitle.isNotEmpty()) {
                                    EventTimeRow(
                                        start = item.nextStart,
                                        title = item.nextTitle,
                                        endValue = item.nextDuration,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    null
                },
            colors = listRowItemColors()
        )
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
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = start,
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.width(EventStartColumnWidth)
        )
        Text(
            text = title,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        Text(
            text = endValue,
            style = style,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(EventEndColumnWidth)
        )
    }
}

@Composable
private fun ServicePicon(item: ServiceListItem) {
    PiconImage(
        reference = item.reference,
        name = item.name,
        contentDescription = null,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(width = 48.dp, height = 30.dp)
    )
}
