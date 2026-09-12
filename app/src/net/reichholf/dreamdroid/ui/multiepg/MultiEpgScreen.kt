package net.reichholf.dreamdroid.ui.multiepg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Default visible span (GraphMultiEPG default). Zoom comes in Phase 3. */
const val MULTI_EPG_VISIBLE_MINUTES: Int = 120

private val ChannelLabelWidth = 112.dp
private val RowHeight = 48.dp
private val MinBarWidth = 36.dp
/** ~3.dp per minute → 2 h fills ~360.dp of a phone-width pane. */
private val MinuteWidth = 3.dp

/**
 * Performance-oriented MultiEPG grid:
 * - Single [LazyColumn] virtualizes channel rows (only on-screen rows compose).
 * - Shared horizontal scroll state syncs the time ruler with every visible row.
 * - Timeline is a wide [Box]; bars are positioned once (no nested LazyRow).
 * - Caller supplies pre-grouped [channels] built off the UI thread.
 */
@Composable
fun MultiEpgScreen(
    bouquetName: String,
    channels: List<MultiEpgChannel>,
    timelineStartSec: Long,
    timelineEndSec: Long,
    nowSec: Long,
    loading: Boolean,
    errorMessage: String?,
    onJumpToNow: () -> Unit,
    onEventClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val timelineSeconds = (timelineEndSec - timelineStartSec).coerceAtLeast(60L)
    val timelineWidth = MinuteWidth * (timelineSeconds / 60f)

    LaunchedEffect(channels, timelineStartSec, nowSec, timelineWidth) {
        if (channels.isEmpty()) return@LaunchedEffect
        val nowOffsetMin = ((nowSec - timelineStartSec).coerceAtLeast(0L)) / 60f
        val targetPx = with(density) { (MinuteWidth * nowOffsetMin - 48.dp).toPx() }
            .toInt()
            .coerceAtLeast(0)
        // maxValue may still be 0 on first frame; retry after layout.
        hScroll.scrollTo(targetPx.coerceAtMost(hScroll.maxValue.coerceAtLeast(targetPx)))
    }

    Column(modifier = modifier.fillMaxSize().testTag("multi_epg_screen")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bouquetName.ifBlank { stringResource(R.string.multiepg) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .height(20.dp)
                        .width(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            TextButton(onClick = onJumpToNow) {
                Text(stringResource(R.string.multiepg_now))
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (channels.isEmpty() && !loading && errorMessage == null) {
            Text(
                text = stringResource(R.string.multiepg_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "time_ruler", contentType = "ruler") {
                Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                    Spacer(modifier = Modifier.width(ChannelLabelWidth))
                    MultiEpgTimeRuler(
                        timelineStartSec = timelineStartSec,
                        timelineEndSec = timelineEndSec,
                        timelineWidth = timelineWidth,
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(hScroll),
                    )
                }
            }
            items(
                items = channels,
                key = { it.serviceRef },
                contentType = { "channel" },
            ) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight),
                ) {
                    Box(
                        modifier = Modifier
                            .width(ChannelLabelWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = channel.serviceName,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(hScroll),
                    ) {
                        MultiEpgChannelTimeline(
                            channel = channel,
                            timelineStartSec = timelineStartSec,
                            timelineWidth = timelineWidth,
                            nowSec = nowSec,
                            onEventClick = onEventClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiEpgTimeRuler(
    timelineStartSec: Long,
    timelineEndSec: Long,
    timelineWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val tickFormat = remember {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    }
    Box(modifier = modifier.height(28.dp).width(timelineWidth)) {
        val hourStep = 3600L
        var t = timelineStartSec - (timelineStartSec % hourStep)
        while (t < timelineEndSec) {
            if (t >= timelineStartSec) {
                val offsetMin = (t - timelineStartSec) / 60f
                Text(
                    text = tickFormat.format(Date(t * 1000L)),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .offset(x = MinuteWidth * offsetMin)
                        .padding(start = 2.dp),
                    maxLines = 1,
                )
            }
            t += hourStep
        }
    }
}

@Composable
private fun MultiEpgChannelTimeline(
    channel: MultiEpgChannel,
    timelineStartSec: Long,
    timelineWidth: Dp,
    nowSec: Long,
    onEventClick: (Event) -> Unit,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primaryContainer
    val barBorder = MaterialTheme.colorScheme.outlineVariant
    val onBar = MaterialTheme.colorScheme.onPrimaryContainer
    val nowColor = MaterialTheme.colorScheme.error
    val timelineEndSec = timelineStartSec + ((timelineWidth / MinuteWidth) * 60f).toLong()

    Box(
        modifier = Modifier
            .width(timelineWidth)
            .height(RowHeight)
            .background(trackColor.copy(alpha = 0.35f))
            .testTag("multi_epg_row"),
    ) {
        for (bar in channel.bars) {
            val startMin = (bar.startSec - timelineStartSec) / 60f
            val durationMin = max((bar.endSec - bar.startSec) / 60f, 1f)
            val x = MinuteWidth * startMin
            val w = (MinuteWidth * durationMin).coerceAtLeast(MinBarWidth)
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .width(w)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp, horizontal = 1.dp)
                    .background(barColor, MaterialTheme.shapes.extraSmall)
                    .border(1.dp, barBorder, MaterialTheme.shapes.extraSmall)
                    .clickable { onEventClick(bar.event) }
                    .semantics { contentDescription = bar.event.title }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = bar.event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = onBar,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (nowSec in timelineStartSec until timelineEndSec) {
            val nowMin = (nowSec - timelineStartSec) / 60f
            Box(
                modifier = Modifier
                    .offset(x = MinuteWidth * nowMin)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(nowColor)
                    .testTag("multi_epg_now_line"),
            )
        }
    }
}
