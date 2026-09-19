package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.material3.Text as PhoneText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
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
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors

private val RulerHeight = 22.dp

/**
 * TV GraphMultiEPG grid. Programme bars are not clickable/focusable — one
 * [onPreviewKeyEvent] host owns the D-pad cursor.
 */
@Composable
fun TvMultiEpgScreen(
    bouquetName: String,
    channels: List<MultiEpgChannel>,
    timelineStartSec: Long,
    timelineEndSec: Long,
    nowSec: Long,
    originFloorSec: Long,
    loading: Boolean,
    errorMessage: String?,
    selectedServiceRef: String,
    selectedStartSec: Long,
    onSelectedChange: (String, Long) -> Unit,
    onJumpToNow: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onVisibleWindow: (Long, Long) -> Unit,
    onEventClick: (Event) -> Unit,
    onBouquetClick: () -> Unit,
    modifier: Modifier = Modifier,
    keysEnabled: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    hScrollState: ScrollState = rememberScrollState(),
    timerClocks: Map<String, MultiEpgTimerClock> = emptyMap(),
    visibleMinutes: Int = MultiEpgZoom.DEFAULT_MINUTES,
    onVisibleMinutesChange: ((Int) -> Unit)? = null,
    textSize: MultiEpgTextSize = MultiEpgTextSize.DEFAULT
) {
    val hScroll = hScrollState
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val rowHeight = textSize.rowHeightDp(fontScale).dp
    val channelLabelWidth = textSize.channelWidthDp.dp
    val clockSize = textSize.clockSizeDp(fontScale).dp
    val eventStyle = when (textSize) {
        MultiEpgTextSize.Compact -> PhoneMaterialTheme.typography.labelSmall
        MultiEpgTextSize.Comfortable -> PhoneMaterialTheme.typography.titleSmall
    }
    val channelStyle = when (textSize) {
        MultiEpgTextSize.Compact -> PhoneMaterialTheme.typography.labelMedium
        MultiEpgTextSize.Comfortable -> PhoneMaterialTheme.typography.titleSmall
    }
    var layoutOriginSec by remember { mutableLongStateOf(0L) }
    val originForLayout =
        if (layoutOriginSec == 0L) timelineStartSec else layoutOriginSec
    var layoutVisibleMinutes by remember {
        mutableIntStateOf(MultiEpgZoom.coerce(visibleMinutes))
    }
    val minuteWidth = MultiEpgZoom.minuteWidthDp(layoutVisibleMinutes).dp
    val timelineSeconds = (timelineEndSec - originForLayout).coerceAtLeast(60L)
    val timelineWidth = minuteWidth * (timelineSeconds / 60f)
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    val gridFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var pendingPanRight by remember { mutableStateOf(false) }

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
            newOriginSec = timelineStartSec
        ) / 60f
        val deltaPx = with(density) { (minuteWidth * deltaMin).toPx() }.toInt()
        hScroll.scrollTo((hScroll.value + deltaPx).coerceAtLeast(0))
        layoutOriginSec = timelineStartSec
    }

    val timelineStartState = rememberUpdatedState(originForLayout)
    val timelineEndState = rememberUpdatedState(timelineEndSec)

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
                timelineStart
            } else {
                val minutePx = with(density) {
                    MultiEpgZoom.minuteWidthDp(layoutVisibleMinutes).dp.toPx()
                }.coerceAtLeast(0.01f)
                val sec = timelineStart + ((hScroll.value / minutePx) * 60f).toLong()
                sec.coerceIn(
                    timelineStart,
                    (timelineEnd - 60L).coerceAtLeast(timelineStart)
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
                    timelineEnd.coerceAtLeast(visibleStartSec + 60L)
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
            layoutVisibleMinutes = coerced
            withFrameNanos { _ -> }
            hScroll.scrollTo(targetPx.coerceAtLeast(0))
        }
    }

    LaunchedEffect(visibleStartSec, visibleEndSec, channels.isNotEmpty()) {
        if (channels.isEmpty() || visibleEndSec <= visibleStartSec) {
            return@LaunchedEffect
        }
        onVisibleWindow(visibleStartSec, visibleEndSec)
    }

    LaunchedEffect(selectedServiceRef, selectedStartSec, originForLayout, layoutVisibleMinutes) {
        val channel = channels.find { it.serviceRef == selectedServiceRef }
            ?: return@LaunchedEffect
        val bar = channel.bars.find { it.startSec == selectedStartSec }
            ?: return@LaunchedEffect
        if (viewportWidthPx <= 0 || originForLayout == 0L) {
            return@LaunchedEffect
        }
        val minuteWidthDp = MultiEpgZoom.minuteWidthDp(layoutVisibleMinutes)
        val x = MultiEpgBarLayout.offsetDp(
            startSec = bar.startSec,
            timelineStartSec = originForLayout,
            minuteWidthDp = minuteWidthDp
        )
        val w = MultiEpgBarLayout.widthDp(
            startSec = bar.startSec,
            endSec = bar.endSec,
            timelineStartSec = originForLayout,
            minuteWidthDp = minuteWidthDp,
            nextStartSec = MultiEpgBarLayout.nextStartSec(channel.bars, bar.startSec)
        )
        val startPx = with(density) { x.dp.toPx() }.toInt()
        val endPx = with(density) { (x + w).dp.toPx() }.toInt()
        val viewStart = hScroll.value
        val viewEnd = viewStart + viewportWidthPx
        val pad = with(density) { 24.dp.toPx() }.toInt()
        when {
            startPx < viewStart + pad -> {
                hScroll.scrollTo((startPx - pad).coerceAtLeast(0))
            }

            endPx > viewEnd - pad -> {
                hScroll.scrollTo((endPx - viewportWidthPx + pad).coerceAtLeast(0))
            }
        }
    }

    LaunchedEffect(selectedServiceRef, channels) {
        val index = channels.indexOfFirst { it.serviceRef == selectedServiceRef }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    LaunchedEffect(
        channels,
        pendingPanRight,
        selectedServiceRef,
        selectedStartSec,
        visibleMinutes
    ) {
        if (!pendingPanRight) {
            return@LaunchedEffect
        }
        val channel = channels.find { it.serviceRef == selectedServiceRef }
            ?: return@LaunchedEffect
        val next = MultiEpgBarLayout.nextStartSec(channel.bars, selectedStartSec)
        if (next != null) {
            pendingPanRight = false
            onSelectedChange(channel.serviceRef, next)
        }
    }

    val todayLabel = stringResource(R.string.multiepg_today)
    val dayLabel = remember(visibleStartSec, nowSec, todayLabel, originForLayout, timelineEndSec) {
        if (timelineEndSec <= originForLayout) {
            ""
        } else {
            MultiEpgTimeLabels.formatVisibleDay(visibleStartSec, nowSec, todayLabel)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("tv_multi_epg_screen")
    ) {
        TvMultiEpgChrome(
            bouquetName = bouquetName,
            dayLabel = dayLabel,
            loading = loading,
            visibleMinutes = layoutVisibleMinutes,
            onBouquetClick = onBouquetClick,
            onJumpToNow = onJumpToNow,
            onPrevDay = onPrevDay,
            onNextDay = onNextDay,
            onRefresh = onRefresh,
            onVisibleMinutesChange = { minutes ->
                onVisibleMinutesChange?.invoke(minutes)
            },
            onDownToGrid = {
                try {
                    gridFocus.requestFocus()
                } catch (_: IllegalStateException) {
                    // Grid not in composition (empty bouquet).
                }
            },
            keysEnabled = keysEnabled
        )
        if (errorMessage != null) {
            PhoneText(
                text = errorMessage,
                color = PhoneMaterialTheme.colorScheme.error,
                style = PhoneMaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        if (channels.isEmpty() && !loading && errorMessage == null) {
            PhoneText(
                text = stringResource(R.string.multiepg_empty),
                style = PhoneMaterialTheme.typography.bodyMedium,
                color = PhoneMaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        val selectedRefState = rememberUpdatedState(selectedServiceRef)
        val selectedStartState = rememberUpdatedState(selectedStartSec)
        val originFloorState = rememberUpdatedState(originFloorSec)
        val channelsState = rememberUpdatedState(channels)
        val visibleMinutesState = rememberUpdatedState(visibleMinutes)
        val zoomWindowSec = {
            MultiEpgZoom.coerce(visibleMinutesState.value) * 60L
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(RulerHeight)
        ) {
            Spacer(modifier = Modifier.width(channelLabelWidth))
            TvMultiEpgTimeRuler(
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
                    .horizontalScroll(hScroll)
            )
        }
        HorizontalDivider(color = PhoneMaterialTheme.colorScheme.outlineVariant)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(gridFocus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (!keysEnabled || !isTvMultiEpgGridKey(event.key)) {
                        return@onPreviewKeyEvent false
                    }
                    if (event.type != KeyEventType.KeyDown &&
                        event.type != KeyEventType.KeyUp
                    ) {
                        return@onPreviewKeyEvent false
                    }
                    val consume = consumesTvMultiEpgGridKey(
                        key = event.key,
                        channels = channelsState.value,
                        selectedServiceRef = selectedRefState.value
                    )
                    if (event.type == KeyEventType.KeyDown && consume) {
                        handleTvMultiEpgGridKey(
                            key = event.key,
                            channels = channelsState.value,
                            selectedServiceRef = selectedRefState.value,
                            selectedStartSec = selectedStartState.value,
                            originFloorSec = originFloorState.value,
                            zoomWindowSec = zoomWindowSec(),
                            onSelectedChange = onSelectedChange,
                            onEventClick = onEventClick,
                            onVisibleWindow = onVisibleWindow,
                            onPendingPanRight = { pendingPanRight = it },
                            onScrollToChannel = { index ->
                                scope.launch { listState.scrollToItem(index) }
                            }
                        )
                    }
                    consume
                }
                .testTag("tv_multi_epg_grid")
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tv_multi_epg_channel_list")
            ) {
                items(
                    items = channels,
                    key = { it.serviceRef },
                    contentType = { "channel" }
                ) { channel ->
                    val selected = channel.serviceRef == selectedServiceRef
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                    ) {
                        TvMultiEpgChannelLabel(
                            serviceName = channel.serviceName,
                            selected = selected,
                            style = channelStyle,
                            modifier = Modifier
                                .width(channelLabelWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onSizeChanged { viewportWidthPx = it.width }
                                .horizontalScroll(hScroll)
                        ) {
                            TvMultiEpgChannelTimeline(
                                channel = channel,
                                timelineStartSec = originForLayout,
                                timelineWidth = timelineWidth,
                                minuteWidth = minuteWidth,
                                nowSec = nowSec,
                                cullStartSec = cullWindow.first,
                                cullEndSec = cullWindow.second,
                                selectedStartSec = if (selected) selectedStartSec else null,
                                timerClocks = timerClocks,
                                rowHeight = rowHeight,
                                eventStyle = eventStyle,
                                clockSize = clockSize
                            )
                        }
                    }
                    HorizontalDivider(
                        color = PhoneMaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = 0.5f
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMultiEpgChrome(
    bouquetName: String,
    dayLabel: String,
    loading: Boolean,
    visibleMinutes: Int,
    onBouquetClick: () -> Unit,
    onJumpToNow: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onRefresh: (() -> Unit)?,
    onVisibleMinutesChange: (Int) -> Unit,
    onDownToGrid: () -> Unit,
    keysEnabled: Boolean
) {
    val hours = MultiEpgZoom.hours(visibleMinutes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("tv_multi_epg_chrome")
            .onPreviewKeyEvent { event ->
                if (!keysEnabled || event.key != Key.DirectionDown) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    onDownToGrid()
                }
                true
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TvMultiEpgChromeChip(
            label = bouquetName,
            tag = "tv_multi_epg_bouquet",
            onClick = onBouquetClick
        )
        if (dayLabel.isNotEmpty()) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (loading) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.labelSmall
            )
        }
        TvMultiEpgChromeChip(
            label = stringResource(R.string.previous_day),
            tag = "tv_multi_epg_prev_day",
            onClick = onPrevDay
        )
        TvMultiEpgChromeChip(
            label = stringResource(R.string.multiepg_now),
            tag = "tv_multi_epg_now",
            onClick = onJumpToNow
        )
        TvMultiEpgChromeChip(
            label = stringResource(R.string.next_day),
            tag = "tv_multi_epg_next_day",
            onClick = onNextDay
        )
        TvMultiEpgChromeChip(
            label = stringResource(R.string.multiepg_zoom_hours, hours),
            tag = "tv_multi_epg_zoom",
            onClick = {
                val options = MultiEpgZoom.OPTIONS_MINUTES
                val index = options.indexOf(MultiEpgZoom.coerce(visibleMinutes))
                val next = options[(index + 1).mod(options.size)]
                onVisibleMinutesChange(next)
            }
        )
        if (onRefresh != null) {
            TvMultiEpgChromeChip(
                label = stringResource(R.string.reload),
                tag = "tv_multi_epg_refresh",
                onClick = onRefresh
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMultiEpgChromeChip(label: String, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        colors = dreamDroidTvCardColors(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TvMultiEpgChannelLabel(
    serviceName: String,
    selected: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val background = if (selected) {
        PhoneMaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val color = if (selected) {
        PhoneMaterialTheme.colorScheme.onPrimaryContainer
    } else {
        PhoneMaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier.background(background),
        contentAlignment = Alignment.CenterStart
    ) {
        PhoneText(
            text = serviceName,
            style = style,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvMultiEpgTimeRuler(
    timelineStartSec: Long,
    timelineEndSec: Long,
    timelineWidth: Dp,
    minuteWidth: Dp,
    tickStepSec: Long,
    cullStartSec: Long,
    cullEndSec: Long,
    modifier: Modifier = Modifier
) {
    val tickFormat = remember {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    }
    val ticks = remember(
        timelineStartSec,
        timelineEndSec,
        cullStartSec,
        cullEndSec,
        tickStepSec
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
            PhoneText(
                text = tickFormat.format(Date(t * 1000L)),
                style = PhoneMaterialTheme.typography.labelSmall,
                color = PhoneMaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .offset(x = minuteWidth * offsetMin)
                    .padding(start = 2.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TvMultiEpgChannelTimeline(
    channel: MultiEpgChannel,
    timelineStartSec: Long,
    timelineWidth: Dp,
    minuteWidth: Dp,
    nowSec: Long,
    cullStartSec: Long,
    cullEndSec: Long,
    selectedStartSec: Long?,
    timerClocks: Map<String, MultiEpgTimerClock>,
    rowHeight: Dp,
    eventStyle: TextStyle,
    clockSize: Dp
) {
    val trackColor = PhoneMaterialTheme.colorScheme.surface
    val barColor = PhoneMaterialTheme.colorScheme.surfaceVariant
    val selectedBarColor = PhoneMaterialTheme.colorScheme.primaryContainer
    val onBar = PhoneMaterialTheme.colorScheme.onSurfaceVariant
    val onSelectedBar = PhoneMaterialTheme.colorScheme.onPrimaryContainer
    val nowColor = PhoneMaterialTheme.colorScheme.primary
    val timelineEndSec = timelineStartSec + ((timelineWidth / minuteWidth) * 60f).toLong()
    val visibleBars = remember(channel.bars, cullStartSec, cullEndSec) {
        channel.bars.overlapping(cullStartSec, cullEndSec)
    }

    Box(
        modifier = Modifier
            .width(timelineWidth)
            .height(rowHeight)
            .background(trackColor)
    ) {
        for (bar in visibleBars) {
            key(channel.serviceRef, bar.event.eventId, bar.startSec) {
                val selected = selectedStartSec != null && bar.startSec == selectedStartSec
                TvProgrammeBar(
                    bar = bar,
                    timelineStartSec = timelineStartSec,
                    minuteWidth = minuteWidth,
                    barColor = if (selected) selectedBarColor else barColor,
                    onBar = if (selected) onSelectedBar else onBar,
                    eventStyle = eventStyle,
                    clockSize = clockSize,
                    clock = timerClocks[
                        multiEpgTimerClockKey(
                            channel.serviceRef,
                            bar.event.eventId,
                            bar.startSec
                        )
                    ],
                    nextStartSec = MultiEpgBarLayout.nextStartSec(
                        channel.bars,
                        bar.startSec
                    )
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
            )
        }
    }
}

@Composable
private fun TvProgrammeBar(
    bar: MultiEpgBar,
    timelineStartSec: Long,
    minuteWidth: Dp,
    barColor: Color,
    onBar: Color,
    eventStyle: TextStyle,
    clockSize: Dp,
    clock: MultiEpgTimerClock?,
    nextStartSec: Long?
) {
    val drawStart = maxOf(bar.startSec, timelineStartSec)
    if (bar.endSec <= timelineStartSec) {
        return
    }
    val minuteWidthDp = minuteWidth.value
    val x = MultiEpgBarLayout.offsetDp(
        startSec = drawStart,
        timelineStartSec = timelineStartSec,
        minuteWidthDp = minuteWidthDp
    ).dp
    val w = MultiEpgBarLayout.widthDp(
        startSec = bar.startSec,
        endSec = bar.endSec,
        timelineStartSec = timelineStartSec,
        minuteWidthDp = minuteWidthDp,
        nextStartSec = nextStartSec
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
            .background(barColor, PhoneMaterialTheme.shapes.extraSmall)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        PhoneText(
            text = bar.event.title,
            style = eventStyle,
            color = onBar,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = if (clock != null) clockSize + 2.dp else 0.dp)
        )
        if (clock != null) {
            val record = clock == MultiEpgTimerClock.Record
            val clockCd = stringResource(
                if (record) {
                    R.string.multiepg_timer_record
                } else {
                    R.string.multiepg_timer_zap
                }
            )
            val clockTint = if (record) {
                PhoneMaterialTheme.colorScheme.primary
            } else {
                PhoneMaterialTheme.colorScheme.tertiary
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(clockSize)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_multiepg_clock),
                    contentDescription = clockCd,
                    tint = clockTint,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(clockSize * 0.35f)
                        .background(
                            color = clockTint,
                            shape = if (record) {
                                CircleShape
                            } else {
                                RoundedCornerShape(1.dp)
                            }
                        )
                )
            }
        }
    }
}

private fun isTvMultiEpgGridKey(key: Key): Boolean = key == Key.DirectionUp ||
    key == Key.DirectionDown ||
    key == Key.DirectionLeft ||
    key == Key.DirectionRight ||
    key == Key.DirectionCenter ||
    key == Key.Enter

/**
 * Keep Left/Right/Center on the grid even at a bar edge. Up on the first
 * channel is not consumed so focus can return to chrome.
 */
internal fun consumesTvMultiEpgGridKey(
    key: Key,
    channels: List<MultiEpgChannel>,
    selectedServiceRef: String
): Boolean {
    if (key != Key.DirectionUp) {
        return true
    }
    if (channels.isEmpty()) {
        return false
    }
    val index = channels.indexOfFirst { it.serviceRef == selectedServiceRef }
        .coerceAtLeast(0)
    return index > 0
}

private fun handleTvMultiEpgGridKey(
    key: Key,
    channels: List<MultiEpgChannel>,
    selectedServiceRef: String,
    selectedStartSec: Long,
    originFloorSec: Long,
    zoomWindowSec: Long,
    onSelectedChange: (String, Long) -> Unit,
    onEventClick: (Event) -> Unit,
    onVisibleWindow: (Long, Long) -> Unit,
    onPendingPanRight: (Boolean) -> Unit,
    onScrollToChannel: (Int) -> Unit
) {
    if (channels.isEmpty()) {
        return
    }
    val index = channels.indexOfFirst { it.serviceRef == selectedServiceRef }
        .coerceAtLeast(0)
    val channel = channels.getOrNull(index) ?: return
    when (key) {
        Key.DirectionUp -> {
            val prevIndex = index - 1
            if (prevIndex < 0) {
                return
            }
            moveToChannel(
                channels[prevIndex],
                prevIndex,
                selectedStartSec,
                onSelectedChange,
                onScrollToChannel
            )
        }

        Key.DirectionDown -> {
            val nextIndex = index + 1
            if (nextIndex >= channels.size) {
                return
            }
            moveToChannel(
                channels[nextIndex],
                nextIndex,
                selectedStartSec,
                onSelectedChange,
                onScrollToChannel
            )
        }

        Key.DirectionLeft -> {
            if (channel.bars.isEmpty()) {
                return
            }
            if (selectedStartSec <= originFloorSec) {
                return
            }
            val prev = MultiEpgBarLayout.prevStartSec(channel.bars, selectedStartSec)
                ?: return
            onPendingPanRight(false)
            onSelectedChange(channel.serviceRef, prev)
        }

        Key.DirectionRight -> {
            if (channel.bars.isEmpty()) {
                return
            }
            val next = MultiEpgBarLayout.nextStartSec(channel.bars, selectedStartSec)
            if (next != null) {
                onPendingPanRight(false)
                onSelectedChange(channel.serviceRef, next)
            } else {
                onPendingPanRight(true)
                val start = selectedStartSec
                onVisibleWindow(start, start + zoomWindowSec)
            }
        }

        Key.DirectionCenter, Key.Enter -> {
            val bar = channel.bars.find { it.startSec == selectedStartSec }
            if (bar != null) {
                onEventClick(bar.event)
            }
        }
    }
}

private fun moveToChannel(
    channel: MultiEpgChannel,
    index: Int,
    selectedStartSec: Long,
    onSelectedChange: (String, Long) -> Unit,
    onScrollToChannel: (Int) -> Unit
) {
    val start = startOnChannel(channel, selectedStartSec)
    onSelectedChange(channel.serviceRef, start)
    onScrollToChannel(index)
}

private fun startOnChannel(channel: MultiEpgChannel, selectedStartSec: Long): Long {
    if (channel.bars.isEmpty()) {
        return selectedStartSec
    }
    val exact = channel.bars.find { it.startSec == selectedStartSec }
    if (exact != null) {
        return exact.startSec
    }
    val overlap = channel.bars.firstOrNull { bar ->
        bar.startSec <= selectedStartSec && bar.endSec > selectedStartSec
    }
    return overlap?.startSec ?: channel.bars.first().startSec
}
