package net.reichholf.dreamdroid.ui.multiepg

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgTimeLabels
import net.reichholf.dreamdroid.multiepg.overlapping
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Default visible span (GraphMultiEPG default). Zoom comes in Phase 3. */
const val MULTI_EPG_VISIBLE_MINUTES: Int = 120

private val ChannelLabelWidth = 100.dp
private val RowHeight = 36.dp
private val RulerHeight = 22.dp
private val MinBarWidth = 28.dp
/** ~3.dp per minute → 2 h fills ~360.dp of a phone-width pane. */
private val MinuteWidth = 3.dp

/**
 * MultiEPG grid aligned with the rest of the app's Material surfaces.
 *
 * Performance:
 * - [LazyColumn] virtualizes channel rows
 * - Shared [horizontalScroll] syncs the ruler with visible rows
 * - Programme bars are viewport-culled so a 24 h chunk does not compose off-screen nodes
 */
@Composable
fun MultiEpgScreen(
    bouquetName: String,
    channels: List<MultiEpgChannel>,
    timelineStartSec: Long,
    timelineEndSec: Long,
    nowSec: Long,
    loading: Boolean,
    pullRefreshing: Boolean = false,
    errorMessage: String?,
    onJumpToNow: () -> Unit,
    onPrevDay: (() -> Unit)? = null,
    onNextDay: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onVisibleWindow: ((visibleStartSec: Long, visibleEndSec: Long) -> Unit)? = null,
    onEventClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    focusSec: Long = nowSec,
    focusEpoch: Int = 0,
) {
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val timelineSeconds = (timelineEndSec - timelineStartSec).coerceAtLeast(60L)
    val timelineWidth = MinuteWidth * (timelineSeconds / 60f)
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var prevTimelineStartSec by remember { mutableLongStateOf(Long.MIN_VALUE) }

    LaunchedEffect(focusEpoch, channels.isNotEmpty()) {
        if (channels.isEmpty() || timelineEndSec <= timelineStartSec) {
            return@LaunchedEffect
        }
        val nowOffsetMin = ((focusSec - timelineStartSec).coerceAtLeast(0L)) / 60f
        val targetPx = with(density) { (MinuteWidth * nowOffsetMin - 48.dp).toPx() }
            .toInt()
            .coerceAtLeast(0)
        hScroll.scrollTo(targetPx.coerceAtMost(hScroll.maxValue.coerceAtLeast(targetPx)))
    }

    LaunchedEffect(timelineStartSec, channels.isEmpty()) {
        if (channels.isEmpty() || timelineStartSec == 0L) {
            prevTimelineStartSec = Long.MIN_VALUE
            return@LaunchedEffect
        }
        val previous = prevTimelineStartSec
        prevTimelineStartSec = timelineStartSec
        if (previous == Long.MIN_VALUE) {
            return@LaunchedEffect
        }
        val deltaMin = (previous - timelineStartSec) / 60f
        val deltaPx = with(density) { (MinuteWidth * deltaMin).toPx() }.toInt()
        hScroll.scrollTo((hScroll.value + deltaPx).coerceAtLeast(0))
    }

    val cullWindow by remember {
        derivedStateOf {
            if (viewportWidthPx <= 0) {
                timelineStartSec to timelineEndSec
            } else {
                val minutePx = with(density) { MinuteWidth.toPx() }.coerceAtLeast(0.01f)
                val bucketPx = minutePx * 5f
                val startPx = (hScroll.value / bucketPx).toInt() * bucketPx
                val padMin = 20f
                val startSec = timelineStartSec +
                    (((startPx / minutePx) - padMin) * 60f).toLong()
                val endSec = timelineStartSec +
                    ((((startPx + viewportWidthPx) / minutePx) + padMin) * 60f).toLong()
                startSec to endSec
            }
        }
    }

    val visibleStartSec by remember {
        derivedStateOf {
            if (viewportWidthPx <= 0 || timelineEndSec <= timelineStartSec) {
                focusSec.coerceIn(
                    timelineStartSec,
                    (timelineEndSec - 60L).coerceAtLeast(timelineStartSec),
                )
            } else {
                val minutePx = with(density) { MinuteWidth.toPx() }.coerceAtLeast(0.01f)
                val sec = timelineStartSec +
                    ((hScroll.value / minutePx) * 60f).toLong()
                sec.coerceIn(
                    timelineStartSec,
                    (timelineEndSec - 60L).coerceAtLeast(timelineStartSec),
                )
            }
        }
    }
    val visibleEndSec by remember {
        derivedStateOf {
            if (viewportWidthPx <= 0 || timelineEndSec <= timelineStartSec) {
                (visibleStartSec + MULTI_EPG_VISIBLE_MINUTES * 60L)
                    .coerceAtMost(timelineEndSec.coerceAtLeast(visibleStartSec + 60L))
            } else {
                val minutePx = with(density) { MinuteWidth.toPx() }.coerceAtLeast(0.01f)
                val sec = timelineStartSec +
                    (((hScroll.value + viewportWidthPx) / minutePx) * 60f).toLong()
                sec.coerceIn(
                    (visibleStartSec + 60L).coerceAtMost(timelineEndSec),
                    timelineEndSec.coerceAtLeast(visibleStartSec + 60L),
                )
            }
        }
    }

    LaunchedEffect(visibleStartSec, visibleEndSec, channels.isNotEmpty(), onVisibleWindow) {
        val cb = onVisibleWindow ?: return@LaunchedEffect
        if (channels.isEmpty() || visibleEndSec <= visibleStartSec) {
            return@LaunchedEffect
        }
        cb(visibleStartSec, visibleEndSec)
    }
    val todayLabel = stringResource(R.string.multiepg_today)
    val dayLabel = remember(visibleStartSec, nowSec, todayLabel) {
        if (timelineEndSec <= timelineStartSec) {
            ""
        } else {
            MultiEpgTimeLabels.formatVisibleDay(visibleStartSec, nowSec, todayLabel)
        }
    }

    DreamDroidPullRefresh(
        refreshing = pullRefreshing,
        onRefresh = { onRefresh?.invoke() },
        enabled = onRefresh != null,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize().testTag("multi_epg_screen")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = bouquetName.ifBlank { stringResource(R.string.multiepg) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .height(18.dp)
                            .width(18.dp)
                            .testTag("multi_epg_sync_indicator"),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (onPrevDay != null) {
                    TextButton(onClick = onPrevDay) {
                        Text(stringResource(R.string.multiepg_prev_day))
                    }
                }
                TextButton(onClick = onJumpToNow) {
                    Text(stringResource(R.string.multiepg_now))
                }
                if (onNextDay != null) {
                    TextButton(onClick = onNextDay) {
                        Text(stringResource(R.string.multiepg_next_day))
                    }
                }
            }

            if (dayLabel.isNotEmpty()) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 0.dp)
                        .testTag("multi_epg_day_label"),
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            if (channels.isEmpty() && !loading && errorMessage == null) {
                Text(
                    text = stringResource(R.string.multiepg_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                return@DreamDroidPullRefresh
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "time_ruler", contentType = "ruler") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RulerHeight),
                    ) {
                        Spacer(modifier = Modifier.width(ChannelLabelWidth))
                        MultiEpgTimeRuler(
                            timelineStartSec = timelineStartSec,
                            timelineEndSec = timelineEndSec,
                            timelineWidth = timelineWidth,
                            cullStartSec = cullWindow.first,
                            cullEndSec = cullWindow.second,
                            modifier = Modifier
                                .weight(1f)
                                .onSizeChanged { viewportWidthPx = it.width }
                                .horizontalScroll(hScroll),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onSizeChanged { viewportWidthPx = it.width }
                                .horizontalScroll(hScroll),
                        ) {
                            MultiEpgChannelTimeline(
                                channel = channel,
                                timelineStartSec = timelineStartSec,
                                timelineWidth = timelineWidth,
                                nowSec = nowSec,
                                cullStartSec = cullWindow.first,
                                cullEndSec = cullWindow.second,
                                onEventClick = onEventClick,
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
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
    cullStartSec: Long,
    cullEndSec: Long,
    modifier: Modifier = Modifier,
) {
    val tickFormat = remember {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    }
    val ticks = remember(timelineStartSec, timelineEndSec, cullStartSec, cullEndSec) {
        val hourStep = 3600L
        var t = timelineStartSec - (timelineStartSec % hourStep)
        val list = ArrayList<Long>(32)
        while (t < timelineEndSec) {
            if (t >= timelineStartSec &&
                t >= cullStartSec - hourStep &&
                t <= cullEndSec + hourStep
            ) {
                list.add(t)
            }
            t += hourStep
        }
        list
    }
    Box(modifier = modifier.height(RulerHeight).width(timelineWidth)) {
        for (t in ticks) {
            val offsetMin = (t - timelineStartSec) / 60f
            Text(
                text = tickFormat.format(Date(t * 1000L)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .offset(x = MinuteWidth * offsetMin)
                    .padding(start = 2.dp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MultiEpgChannelTimeline(
    channel: MultiEpgChannel,
    timelineStartSec: Long,
    timelineWidth: Dp,
    nowSec: Long,
    cullStartSec: Long,
    cullEndSec: Long,
    onEventClick: (Event) -> Unit,
) {
    // Match list-EPG cards: surfaceVariant bars, not loud primaryContainer demo chrome.
    val trackColor = MaterialTheme.colorScheme.surface
    val barColor = MaterialTheme.colorScheme.surfaceVariant
    val onBar = MaterialTheme.colorScheme.onSurface
    val nowColor = MaterialTheme.colorScheme.primary
    val timelineEndSec = timelineStartSec + ((timelineWidth / MinuteWidth) * 60f).toLong()

    val visibleBars = remember(channel.bars, cullStartSec, cullEndSec) {
        channel.bars.overlapping(cullStartSec, cullEndSec)
    }

    Box(
        modifier = Modifier
            .width(timelineWidth)
            .height(RowHeight)
            .background(trackColor)
            .testTag("multi_epg_row"),
    ) {
        for (bar in visibleBars) {
            key(channel.serviceRef, bar.event.eventId, bar.startSec) {
                ProgrammeBar(
                    bar = bar,
                    timelineStartSec = timelineStartSec,
                    barColor = barColor,
                    onBar = onBar,
                    onEventClick = onEventClick,
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

@Composable
private fun ProgrammeBar(
    bar: MultiEpgBar,
    timelineStartSec: Long,
    barColor: Color,
    onBar: Color,
    onEventClick: (Event) -> Unit,
) {
    val drawStart = max(bar.startSec, timelineStartSec)
    if (bar.endSec <= timelineStartSec) {
        return
    }
    val startMin = (drawStart - timelineStartSec) / 60f
    val durationMin = max((bar.endSec - drawStart) / 60f, 1f)
    val x = MinuteWidth * startMin
    val w = (MinuteWidth * durationMin).coerceAtLeast(MinBarWidth)
    Box(
        modifier = Modifier
            .offset(x = x)
            .width(w)
            .fillMaxHeight()
            .padding(vertical = 1.dp, horizontal = 0.5.dp)
            .background(barColor, MaterialTheme.shapes.extraSmall)
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
