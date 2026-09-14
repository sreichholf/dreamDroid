package net.reichholf.dreamdroid.ui.multiepg

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgBarLayout
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import net.reichholf.dreamdroid.multiepg.MultiEpgTimeLabels
import net.reichholf.dreamdroid.multiepg.MultiEpgTimerClock
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.MultiEpgZoom
import net.reichholf.dreamdroid.multiepg.multiEpgTimerClockKey
import net.reichholf.dreamdroid.multiepg.overlapping
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Default visible span (GraphMultiEPG default). */
const val MULTI_EPG_VISIBLE_MINUTES: Int = MultiEpgZoom.DEFAULT_MINUTES

private val RulerHeight = 22.dp

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
    hScrollState: ScrollState = rememberScrollState(),
    focusSec: Long = nowSec,
    focusEpoch: Int = 0,
    timerClocks: Map<String, MultiEpgTimerClock> = emptyMap(),
    visibleMinutes: Int = MULTI_EPG_VISIBLE_MINUTES,
    onVisibleMinutesChange: ((Int) -> Unit)? = null,
    textSize: MultiEpgTextSize = MultiEpgTextSize.DEFAULT,
) {
    val hScroll = hScrollState
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val rowHeight = textSize.rowHeightDp(fontScale).dp
    val channelLabelWidth = textSize.channelWidthDp.dp
    val clockSize = textSize.clockSizeDp(fontScale).dp
    val eventStyle = when (textSize) {
        MultiEpgTextSize.Compact -> MaterialTheme.typography.labelSmall
        MultiEpgTextSize.Comfortable -> MaterialTheme.typography.titleSmall
    }
    val channelStyle = when (textSize) {
        MultiEpgTextSize.Compact -> MaterialTheme.typography.labelMedium
        MultiEpgTextSize.Comfortable -> MaterialTheme.typography.titleSmall
    }
    // Keep painting the last committed origin until scroll is shifted. Using the
    // new painted start in this frame moves every bar and the day label before
    // horizontalScroll can catch up.
    var layoutOriginSec by remember { mutableLongStateOf(0L) }
    val originForLayout =
        if (layoutOriginSec == 0L) timelineStartSec else layoutOriginSec
    // Same hold for zoom: new dp/minute with the old scroll offset would jump
    // every bar and the day label for one frame.
    var layoutVisibleMinutes by remember {
        mutableIntStateOf(MultiEpgZoom.coerce(visibleMinutes))
    }
    val minuteWidth = MultiEpgZoom.minuteWidthDp(layoutVisibleMinutes).dp
    val timelineSeconds = (timelineEndSec - originForLayout).coerceAtLeast(60L)
    val timelineWidth = minuteWidth * (timelineSeconds / 60f)
    var viewportWidthPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(focusEpoch, channels.isNotEmpty()) {
        if (channels.isEmpty() || timelineEndSec <= originForLayout) {
            return@LaunchedEffect
        }
        val nowOffsetMin = ((focusSec - originForLayout).coerceAtLeast(0L)) / 60f
        val targetPx = with(density) { (minuteWidth * nowOffsetMin - 48.dp).toPx() }
            .toInt()
            .coerceAtLeast(0)
        hScroll.scrollTo(targetPx.coerceAtMost(hScroll.maxValue.coerceAtLeast(targetPx)))
    }

    LaunchedEffect(timelineStartSec) {
        if (timelineStartSec == 0L) {
            layoutOriginSec = 0L
            return@LaunchedEffect
        }
        val previous = layoutOriginSec
        if (previous == 0L) {
            layoutOriginSec = timelineStartSec
            return@LaunchedEffect
        }
        if (previous == timelineStartSec) {
            return@LaunchedEffect
        }
        val deltaMin = MultiEpgWindows.originScrollCompensationSec(
            previousOriginSec = previous,
            newOriginSec = timelineStartSec,
        ) / 60f
        val deltaPx = with(density) { (minuteWidth * deltaMin).toPx() }.toInt()
        hScroll.scrollTo((hScroll.value + deltaPx).coerceAtLeast(0))
        layoutOriginSec = timelineStartSec
    }

    val timelineStartState = rememberUpdatedState(originForLayout)
    val timelineEndState = rememberUpdatedState(timelineEndSec)
    val focusSecState = rememberUpdatedState(focusSec)

    val cullWindow by remember {
        derivedStateOf {
            val timelineStart = timelineStartState.value
            val timelineEnd = timelineEndState.value
            if (viewportWidthPx <= 0) {
                timelineStart to timelineEnd
            } else {
                val minutePx = with(density) {
                    MultiEpgZoom.minuteWidthDp(layoutVisibleMinutes).dp.toPx()
                }.coerceAtLeast(0.01f)
                val bucketPx = minutePx * 5f
                val startPx = (hScroll.value / bucketPx).toInt() * bucketPx
                val padMin = 20f
                val startSec = timelineStart +
                    (((startPx / minutePx) - padMin) * 60f).toLong()
                val endSec = timelineStart +
                    ((((startPx + viewportWidthPx) / minutePx) + padMin) * 60f).toLong()
                startSec to endSec
            }
        }
    }

    val visibleStartSec by remember {
        derivedStateOf {
            val timelineStart = timelineStartState.value
            val timelineEnd = timelineEndState.value
            if (viewportWidthPx <= 0 || timelineEnd <= timelineStart) {
                focusSecState.value.coerceIn(
                    timelineStart,
                    (timelineEnd - 60L).coerceAtLeast(timelineStart),
                )
            } else {
                val minutePx = with(density) {
                    MultiEpgZoom.minuteWidthDp(layoutVisibleMinutes).dp.toPx()
                }.coerceAtLeast(0.01f)
                val sec = timelineStart +
                    ((hScroll.value / minutePx) * 60f).toLong()
                sec.coerceIn(
                    timelineStart,
                    (timelineEnd - 60L).coerceAtLeast(timelineStart),
                )
            }
        }
    }
    val visibleEndSec by remember {
        derivedStateOf {
            val timelineStart = timelineStartState.value
            val timelineEnd = timelineEndState.value
            if (viewportWidthPx <= 0 || timelineEnd <= timelineStart) {
                (visibleStartSec + layoutVisibleMinutes * 60L)
                    .coerceAtMost(timelineEnd.coerceAtLeast(visibleStartSec + 60L))
            } else {
                val minutePx = with(density) {
                    MultiEpgZoom.minuteWidthDp(layoutVisibleMinutes).dp.toPx()
                }.coerceAtLeast(0.01f)
                val sec = timelineStart +
                    (((hScroll.value + viewportWidthPx) / minutePx) * 60f).toLong()
                sec.coerceIn(
                    (visibleStartSec + 60L).coerceAtMost(timelineEnd),
                    timelineEnd.coerceAtLeast(visibleStartSec + 60L),
                )
            }
        }
    }

    LaunchedEffect(visibleMinutes) {
        val coerced = MultiEpgZoom.coerce(visibleMinutes)
        val previous = layoutVisibleMinutes
        if (previous == coerced) {
            return@LaunchedEffect
        }
        val keepSec = visibleStartSec
        val newMinuteWidth = MultiEpgZoom.minuteWidthDp(coerced).dp
        val offsetMin = ((keepSec - originForLayout).coerceAtLeast(0L)) / 60f
        val targetPx = with(density) { (newMinuteWidth * offsetMin).toPx() }
            .toInt()
            .coerceAtLeast(0)
        if (targetPx <= hScroll.maxValue) {
            hScroll.scrollTo(targetPx)
            layoutVisibleMinutes = coerced
        } else {
            // Zoom-in grows the scroll range; commit density first so maxValue
            // can accept the compensated offset.
            layoutVisibleMinutes = coerced
            withFrameNanos { _ -> }
            hScroll.scrollTo(targetPx.coerceAtLeast(0))
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
    val paneTitleText = bouquetName.ifBlank { stringResource(R.string.multiepg) }
    val dayLabel = remember(visibleStartSec, nowSec, todayLabel) {
            if (timelineEndSec <= originForLayout) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("multi_epg_screen")
                .semantics { paneTitle = paneTitleText },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Bouquet name lives on the activity toolbar; keep this row for
                // Now / ±day / zoom so it does not duplicate the title chrome.
                Spacer(modifier = Modifier.weight(1f))
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
                MultiEpgZoomButton(
                    visibleMinutes = layoutVisibleMinutes,
                    onVisibleMinutesChange = { minutes ->
                        onVisibleMinutesChange?.invoke(minutes)
                    },
                )
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RulerHeight)
                    .testTag("multi_epg_time_ruler"),
            ) {
                Spacer(modifier = Modifier.width(channelLabelWidth))
                MultiEpgTimeRuler(
                    timelineStartSec = originForLayout,
                    timelineEndSec = timelineEndSec,
                    timelineWidth = timelineWidth,
                    minuteWidth = minuteWidth,
                    tickStepSec = MultiEpgZoom.tickStepSec(layoutVisibleMinutes),
                    cullStartSec = cullWindow.first,
                    cullEndSec = cullWindow.second,
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { viewportWidthPx = it.width }
                        .horizontalScroll(hScroll),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("multi_epg_channel_list"),
            ) {
                items(
                    items = channels,
                    key = { it.serviceRef },
                    contentType = { "channel" },
                ) { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(channelLabelWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = channel.serviceName,
                                style = channelStyle,
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
                                timelineStartSec = originForLayout,
                                timelineWidth = timelineWidth,
                                minuteWidth = minuteWidth,
                                nowSec = nowSec,
                                cullStartSec = cullWindow.first,
                                cullEndSec = cullWindow.second,
                                timerClocks = timerClocks,
                                rowHeight = rowHeight,
                                eventStyle = eventStyle,
                                clockSize = clockSize,
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
private fun MultiEpgZoomButton(
    visibleMinutes: Int,
    onVisibleMinutesChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hours = MultiEpgZoom.hours(visibleMinutes)
    val zoomCd = stringResource(R.string.multiepg_zoom)
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .testTag("multi_epg_zoom")
                .semantics { contentDescription = zoomCd },
        ) {
            Text(stringResource(R.string.multiepg_zoom_hours, hours))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (minutes in MultiEpgZoom.OPTIONS_MINUTES) {
                val optionHours = MultiEpgZoom.hours(minutes)
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.multiepg_zoom_hours, optionHours))
                    },
                    onClick = {
                        onVisibleMinutesChange(minutes)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MultiEpgTimeRuler(
    timelineStartSec: Long,
    timelineEndSec: Long,
    timelineWidth: Dp,
    minuteWidth: Dp,
    tickStepSec: Long,
    cullStartSec: Long,
    cullEndSec: Long,
    modifier: Modifier = Modifier,
) {
    val tickFormat = remember {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    }
    val ticks = remember(
        timelineStartSec,
        timelineEndSec,
        cullStartSec,
        cullEndSec,
        tickStepSec,
    ) {
        var t = timelineStartSec - (timelineStartSec % tickStepSec)
        val list = ArrayList<Long>(32)
        while (t < timelineEndSec) {
            if (t >= timelineStartSec &&
                t >= cullStartSec - tickStepSec &&
                t <= cullEndSec + tickStepSec
            ) {
                list.add(t)
            }
            t += tickStepSec
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
                    .offset(x = minuteWidth * offsetMin)
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
    minuteWidth: Dp,
    nowSec: Long,
    cullStartSec: Long,
    cullEndSec: Long,
    timerClocks: Map<String, MultiEpgTimerClock>,
    rowHeight: Dp,
    eventStyle: TextStyle,
    clockSize: Dp,
    onEventClick: (Event) -> Unit,
) {
    // Match list-EPG cards: surfaceVariant bars, not loud primaryContainer demo chrome.
    val trackColor = MaterialTheme.colorScheme.surface
    val barColor = MaterialTheme.colorScheme.surfaceVariant
    val onBar = MaterialTheme.colorScheme.onSurface
    val nowColor = MaterialTheme.colorScheme.primary
    val timelineEndSec = timelineStartSec + ((timelineWidth / minuteWidth) * 60f).toLong()

    val visibleBars = remember(channel.bars, cullStartSec, cullEndSec) {
        channel.bars.overlapping(cullStartSec, cullEndSec)
    }

    Box(
        modifier = Modifier
            .width(timelineWidth)
            .height(rowHeight)
            .background(trackColor)
            .testTag("multi_epg_row"),
    ) {
        for (bar in visibleBars) {
            key(channel.serviceRef, bar.event.eventId, bar.startSec) {
                ProgrammeBar(
                    bar = bar,
                    timelineStartSec = timelineStartSec,
                    minuteWidth = minuteWidth,
                    barColor = barColor,
                    onBar = onBar,
                    eventStyle = eventStyle,
                    clockSize = clockSize,
                    clock = timerClocks[
                        multiEpgTimerClockKey(
                            channel.serviceRef,
                            bar.event.eventId,
                            bar.startSec,
                        ),
                    ],
                    nextStartSec = MultiEpgBarLayout.nextStartSec(
                        channel.bars,
                        bar.startSec,
                    ),
                    onEventClick = onEventClick,
                )
            }
        }
        if (nowSec in timelineStartSec until timelineEndSec) {
            val nowMin = (nowSec - timelineStartSec) / 60f
            Box(
                modifier = Modifier
                    .offset(x = minuteWidth * nowMin)
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
    minuteWidth: Dp,
    barColor: Color,
    onBar: Color,
    eventStyle: TextStyle,
    clockSize: Dp,
    clock: MultiEpgTimerClock?,
    nextStartSec: Long?,
    onEventClick: (Event) -> Unit,
) {
    val drawStart = maxOf(bar.startSec, timelineStartSec)
    if (bar.endSec <= timelineStartSec) {
        return
    }
    val minuteWidthDp = minuteWidth.value
    val x = MultiEpgBarLayout.offsetDp(
        startSec = drawStart,
        timelineStartSec = timelineStartSec,
        minuteWidthDp = minuteWidthDp,
    ).dp
    val w = MultiEpgBarLayout.widthDp(
        startSec = bar.startSec,
        endSec = bar.endSec,
        timelineStartSec = timelineStartSec,
        minuteWidthDp = minuteWidthDp,
        nextStartSec = nextStartSec,
    ).dp
    if (w <= 0.dp) {
        return
    }
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
            style = eventStyle,
            color = onBar,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = if (clock != null) clockSize + 2.dp else 0.dp),
        )
        if (clock != null) {
            val record = clock == MultiEpgTimerClock.Record
            val clockCd = stringResource(
                if (record) {
                    R.string.multiepg_timer_record
                } else {
                    R.string.multiepg_timer_zap
                },
            )
            Icon(
                painter = painterResource(R.drawable.ic_multiepg_clock),
                contentDescription = clockCd,
                tint = if (record) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(clockSize)
                    .testTag(
                        if (record) {
                            "multi_epg_timer_record"
                        } else {
                            "multi_epg_timer_zap"
                        },
                    ),
            )
        }
    }
}
