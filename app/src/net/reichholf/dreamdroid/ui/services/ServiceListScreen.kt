package net.reichholf.dreamdroid.ui.services

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.helpers.enigma2.Service
import net.reichholf.dreamdroid.ui.compose.ListRowSurface
import net.reichholf.dreamdroid.ui.compose.ListSectionHeader
import net.reichholf.dreamdroid.ui.compose.RowMenu
import net.reichholf.dreamdroid.ui.compose.RowMenuAction
import net.reichholf.dreamdroid.ui.compose.RowMenuState

enum class ServiceRowAction(override val label: Int, val onlineOnly: Boolean) : RowMenuAction {
    CurrentEvent(R.string.current_event, onlineOnly = false),
    NextEvent(R.string.next_event, onlineOnly = false),
    BrowseEpg(R.string.browse_epg, onlineOnly = false),
    Zap(R.string.zap, onlineOnly = true),
    Stream(R.string.stream, onlineOnly = true)
}

fun serviceRowKey(item: ServiceListItem): String = "${item.index}:${item.reference}"

private val EventStartColumnWidth = 45.dp
private val EventEndColumnWidth = 50.dp

/** Slightly taller than the VLC zap list's 4dp strip so the top-edge progress reads clearly. */
private val ProgressBarHeight = 6.dp

const val SERVICE_LIST_PROGRESS_TAG = "service_list_progress"

/** `grid_max_cols` value for "as many columns as fit". */
const val AUTO_FIT_COLUMNS = -1

/** Narrowest service column, as the 1.x `AutofitRecyclerView` laid it out. */
private val ServiceColumnMinWidth = 300.dp

/**
 * The service list as a grid of [ServiceColumnMinWidth] columns, at most [maxColumns] of
 * them ([AUTO_FIT_COLUMNS] or any value below 1: as many as fit).
 */
@Composable
fun ServiceListScreen(
    items: List<ServiceListItem>,
    onItemClick: (ServiceListItem) -> Unit,
    onItemLongClick: (ServiceListItem) -> Unit,
    modifier: Modifier = Modifier,
    maxColumns: Int = AUTO_FIT_COLUMNS,
    menu: RowMenuState<ServiceRowAction>? = null,
    onMenuAction: (ServiceRowAction) -> Unit = {},
    onMenuDismiss: () -> Unit = {}
) {
    LazyVerticalGrid(
        columns = AutoFitCells(ServiceColumnMinWidth, maxColumns),
        modifier = modifier.fillMaxSize()
    ) {
        for (item in items) {
            when {
                item.kind != ServiceRowKind.MARKER -> item(key = serviceRowKey(item)) {
                    Box {
                        ServiceRow(
                            item = item,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) }
                        )
                        RowMenu(serviceRowKey(item), menu, onMenuAction, onMenuDismiss)
                    }
                }

                item.isSectionHeader -> stickyHeader(key = serviceRowKey(item)) {
                    ListSectionHeader(item.name)
                }

                else -> item(key = serviceRowKey(item), span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(SpacerGap))
                }
            }
        }
    }
}

/**
 * [GridCells.Adaptive] capped at [maxCount] columns; [maxCount] below 1 leaves it uncapped.
 * Material 3 and foundation have no capped adaptive grid.
 */
private data class AutoFitCells(private val minSize: Dp, private val maxCount: Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val fit = ((availableSize + spacing) / (minSize.roundToPx() + spacing)).coerceAtLeast(1)
        val count = if (maxCount > 0) minOf(fit, maxCount) else fit
        val cells = availableSize - spacing * (count - 1)
        val size = cells / count
        val remainder = cells % count
        return List(count) { size + if (it < remainder) 1 else 0 }
    }
}

/**
 * [items] as list entries (the phone zap list; [ServiceListScreen] lays them out the same
 * way as a grid): a bouquet marker is a sticky section header over the rows up to
 * the next marker, a spacer is a plain gap, every other item is drawn by [row]. Each item
 * stays one list entry, so an item's index in [items] is its list index.
 */
fun LazyListScope.serviceListItems(
    items: List<ServiceListItem>,
    row: @Composable (ServiceListItem) -> Unit
) {
    for (item in items) {
        when {
            item.kind != ServiceRowKind.MARKER -> item(key = serviceRowKey(item)) { row(item) }

            item.isSectionHeader -> stickyHeader(key = serviceRowKey(item)) {
                ListSectionHeader(item.name)
            }

            else -> item(key = serviceRowKey(item)) {
                Spacer(Modifier.height(SpacerGap))
            }
        }
    }
}

/**
 * A marker with a name. Spacers (`1:832:`) are markers too, but unnamed gaps that must
 * not replace the pinned section header.
 */
val ServiceListItem.isSectionHeader: Boolean
    get() = kind == ServiceRowKind.MARKER && !Service.isSpacer(reference) && name.isNotBlank()

private val SpacerGap = 16.dp

/** A channel or directory row; markers are section headers, see [serviceListItems]. */
@Composable
internal fun ServiceRow(item: ServiceListItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val hasNowNext =
        item.kind == ServiceRowKind.CHANNEL &&
            (item.nowTitle.isNotEmpty() || item.nextTitle.isNotEmpty())
    // The strip sits on the tile above the ListItem. ListItem merges its own semantics, so
    // the click goes on it, next to the label.
    ListRowSurface {
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
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
