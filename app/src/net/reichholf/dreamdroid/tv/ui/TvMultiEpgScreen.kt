package net.reichholf.dreamdroid.tv.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon as PhoneIcon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
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
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvPillShape
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTopBarItemScale
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvTopBarItemColors
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvTopBarItemShape

private val RulerHeight = 28.dp
private val RulerTickHeight = 6.dp
private val NowMarkerWidth = 10.dp
private val NowLineWidth = 2.dp

/** Space between two bars and between two rows. */
private val BarGap = 4.dp
private val BarTextPadding = 8.dp

/** Bars, or the part of a bar left in the viewport, narrower than this show no text. */
private val BarMinTextWidth = 48.dp

/** Bars at least this wide show start – end under the title when the row fits it. */
private val BarSecondLineMinWidth = 120.dp
private val FocusBorderWidth = 2.dp
private val EdgeFade = 32.dp
private val ChromeTargetSize = 48.dp
private val ChromeRailPadding = 4.dp
private val ChromeRailShape = DreamDroidTvPillShape

/** Holes in a row's data shorter than this stay plain gaps instead of "No information". */
private const val NO_INFO_MIN_GAP_SEC: Long = 5 * 60L

/** The selected channel's accent bar at the start of its cell. */
private val ChannelCueWidth = 4.dp
private val ChannelCueHeight = 24.dp

/** The selected row's band: onSurface, faint enough to stay behind the programme bars. */
private const val ROW_BAND_ALPHA = 0.06f

/**
 * TV GraphMultiEPG grid. Programme bars are not clickable/focusable — one
 * [onPreviewKeyEvent] host owns the D-pad cursor, which is drawn with the hub cards'
 * focused colours while the grid has focus and as "selected" while focus is in the chrome.
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
    textSize: MultiEpgTextSize = MultiEpgTextSize.DEFAULT,
    piconsEnabled: Boolean = false
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
    var gridFocused by remember { mutableStateOf(false) }

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
            tvMultiEpgDayLabel(visibleStartSec, nowSec, todayLabel)
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
                nowSec = nowSec,
                cullStartSec = cullWindow.first,
                cullEndSec = cullWindow.second,
                modifier = Modifier
                    .weight(1f)
                    .onSizeChanged { viewportWidthPx = it.width }
                    .testTag("tv_multi_epg_ruler")
                    .horizontalScroll(hScroll)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .tvMultiEpgEdgeFades(hScroll, listState)
                .onFocusChanged { gridFocused = it.isFocused }
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
                            serviceRef = channel.serviceRef,
                            serviceName = channel.serviceName,
                            selected = selected,
                            piconsEnabled = piconsEnabled,
                            style = channelStyle,
                            modifier = Modifier
                                .width(channelLabelWidth)
                                .fillMaxHeight()
                                .testTag("tv_multi_epg_channel_${channel.serviceRef}")
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
                                timelineEndSec = timelineEndSec,
                                timelineWidth = timelineWidth,
                                minuteWidth = minuteWidth,
                                nowSec = nowSec,
                                cullStartSec = cullWindow.first,
                                cullEndSec = cullWindow.second,
                                cursorStartSec = if (selected) selectedStartSec else null,
                                cursorFocused = gridFocused,
                                viewportStartPx = { hScroll.value },
                                selectedRow = selected,
                                timerClocks = timerClocks,
                                rowHeight = rowHeight,
                                eventStyle = eventStyle,
                                clockSize = clockSize
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fades the grid out at its right and bottom edges while there is more to scroll that
 * way, so a programme or row cut by the edge reads as "more", not as the end.
 */
private fun Modifier.tvMultiEpgEdgeFades(hScroll: ScrollState, listState: LazyListState): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = EdgeFade.toPx()
            if (hScroll.canScrollForward) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startX = size.width - fade,
                        endX = size.width
                    ),
                    topLeft = Offset(size.width - fade, 0f),
                    size = Size(fade, size.height),
                    blendMode = BlendMode.DstIn
                )
            }
            if (listState.canScrollForward) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - fade,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, size.height - fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn
                )
            }
        }

/**
 * The toolbar: one rail in the hub cards' container colour holding every control, flat at
 * rest and in the cards' inverse colours when focused, so the row has one colour plus focus.
 */
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .background(PhoneMaterialTheme.colorScheme.surfaceContainerLow, ChromeRailShape)
                .padding(ChromeRailPadding)
                .testTag("tv_multi_epg_chrome_rail"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TvMultiEpgChromeButton(
                tag = "tv_multi_epg_bouquet",
                onClick = onBouquetClick
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bouquetName,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            TvMultiEpgChromeDivider()
            TvMultiEpgChromeIcon(
                iconRes = R.drawable.ic_chevron_left,
                label = stringResource(R.string.previous_day),
                tag = "tv_multi_epg_prev_day",
                onClick = onPrevDay
            )
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.titleSmall,
                color = PhoneMaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .testTag("tv_multi_epg_day")
            )
            TvMultiEpgChromeIcon(
                iconRes = R.drawable.ic_chevron_right,
                label = stringResource(R.string.next_day),
                tag = "tv_multi_epg_next_day",
                onClick = onNextDay
            )
            TvMultiEpgChromeDivider()
            TvMultiEpgChromeText(
                label = stringResource(R.string.multiepg_now),
                tag = "tv_multi_epg_now",
                onClick = onJumpToNow
            )
            TvMultiEpgChromeText(
                label = stringResource(R.string.multiepg_zoom_hours, hours),
                tag = "tv_multi_epg_zoom",
                onClick = {
                    val options = MultiEpgZoom.OPTIONS_MINUTES
                    val index = options.indexOf(MultiEpgZoom.coerce(visibleMinutes))
                    val next = options[(index + 1).mod(options.size)]
                    onVisibleMinutesChange(next)
                },
                contentDescription = stringResource(R.string.multiepg_zoom)
            )
            if (onRefresh != null) {
                TvMultiEpgChromeIcon(
                    iconRes = R.drawable.ic_action_refresh,
                    label = stringResource(R.string.reload),
                    tag = "tv_multi_epg_refresh",
                    onClick = onRefresh
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (loading) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.labelSmall,
                color = PhoneMaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A rail control: styled like the hub top bar's text controls, flat until focused. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvMultiEpgChromeButton(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(ChromeTargetSize)
            .widthIn(min = ChromeTargetSize)
            .testTag(tag),
        shape = dreamDroidTvTopBarItemShape(),
        colors = dreamDroidTvTopBarItemColors(),
        scale = DreamDroidTvTopBarItemScale
    ) {
        Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun TvMultiEpgChromeText(
    label: String,
    tag: String,
    onClick: () -> Unit,
    contentDescription: String? = null
) {
    TvMultiEpgChromeButton(
        tag = tag,
        onClick = onClick,
        modifier = if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun TvMultiEpgChromeIcon(
    @DrawableRes iconRes: Int,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    TvMultiEpgChromeButton(
        tag = tag,
        onClick = onClick,
        modifier = Modifier.width(ChromeTargetSize)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Separates the rail's groups: bouquet, day, then Now / zoom / Reload. */
@Composable
private fun TvMultiEpgChromeDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .size(width = 1.dp, height = 24.dp)
            .background(PhoneMaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * The visible day without the year (the EPG reaches about a week ahead): "Today · Oct 23"
 * for today, the weekday and date ("Fri, Oct 24") for any other day, in the locale's order.
 */
internal fun tvMultiEpgDayLabel(
    visibleSec: Long,
    nowSec: Long,
    todayLabel: String,
    locale: Locale = Locale.getDefault()
): String {
    val today = MultiEpgTimeLabels.sameLocalDay(visibleSec, nowSec)
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(
        locale,
        if (today) "MMMd" else "EEEMMMd"
    )
    val date = SimpleDateFormat(pattern, locale).format(Date(visibleSec * 1000L))
    return if (today) "$todayLabel · $date" else date
}

/**
 * The channel cell. The selected programme's channel is a quiet cue, not a second focus
 * colour: its name in semi-bold [onSurface][androidx.compose.material3.ColorScheme.onSurface],
 * a small primary accent bar at the cell's start, and the row band across the full cell.
 * The name is one line; with picons enabled, the picon replaces it once one loads.
 */
@Composable
private fun TvMultiEpgChannelLabel(
    serviceRef: String,
    serviceName: String,
    selected: Boolean,
    piconsEnabled: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val colors = PhoneMaterialTheme.colorScheme
    var piconLoaded by remember(serviceRef, serviceName, piconsEnabled) {
        mutableStateOf(false)
    }
    Box(
        modifier = modifier.then(
            if (selected) Modifier.background(tvMultiEpgRowBand()) else Modifier
        ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(width = ChannelCueWidth, height = ChannelCueHeight)
                    .background(colors.primary, RoundedCornerShape(percent = 50))
                    .testTag("tv_multi_epg_channel_cue")
            )
        }
        if (piconsEnabled) {
            // Invisible until it loads, so a failed load does not flash the placeholder.
            PiconImage(
                reference = serviceRef,
                name = serviceName,
                contentDescription = serviceName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = BarGap)
                    .alpha(if (piconLoaded) 1f else 0f)
                    .testTag("tv_multi_epg_channel_picon"),
                onSuccess = { piconLoaded = true },
                onError = { piconLoaded = false }
            )
        }
        if (!piconsEnabled || !piconLoaded) {
            PhoneText(
                text = serviceName,
                style = if (selected) style.copy(fontWeight = FontWeight.SemiBold) else style,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("tv_multi_epg_channel_name")
            )
        }
    }
}

/** The faint band behind the selected row, from the channel cell through the timeline. */
@Composable
private fun tvMultiEpgRowBand(): Color =
    PhoneMaterialTheme.colorScheme.onSurface.copy(alpha = ROW_BAND_ALPHA)

/** Hour (or half-hour) labels centred on their tick marks, and the "now" marker. */
@Composable
private fun TvMultiEpgTimeRuler(
    timelineStartSec: Long,
    timelineEndSec: Long,
    timelineWidth: Dp,
    minuteWidth: Dp,
    tickStepSec: Long,
    nowSec: Long,
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
    val tickColor = PhoneMaterialTheme.colorScheme.outline
    val nowColor = PhoneMaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(RulerHeight)
            .width(timelineWidth)
            .drawBehind {
                val minutePx = minuteWidth.toPx()
                val tickHeight = RulerTickHeight.toPx()
                val stroke = 1.dp.toPx()
                for (t in ticks) {
                    val x = (t - timelineStartSec) / 60f * minutePx
                    drawLine(
                        color = tickColor,
                        start = Offset(x, size.height - tickHeight),
                        end = Offset(x, size.height),
                        strokeWidth = stroke
                    )
                    val half = x + tickStepSec / 120f * minutePx
                    drawLine(
                        color = tickColor,
                        start = Offset(half, size.height - tickHeight / 2f),
                        end = Offset(half, size.height),
                        strokeWidth = stroke
                    )
                }
                if (nowSec in timelineStartSec until timelineEndSec) {
                    val x = (nowSec - timelineStartSec) / 60f * minutePx
                    val half = NowMarkerWidth.toPx() / 2f
                    val marker = Path().apply {
                        moveTo(x - half, size.height - half * 1.5f)
                        lineTo(x + half, size.height - half * 1.5f)
                        lineTo(x, size.height)
                        close()
                    }
                    drawPath(marker, nowColor)
                }
            }
    ) {
        for (t in ticks) {
            val offsetMin = (t - timelineStartSec) / 60f
            PhoneText(
                text = tickFormat.format(Date(t * 1000L)),
                style = PhoneMaterialTheme.typography.labelSmall,
                color = PhoneMaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.centeredAtX(minuteWidth * offsetMin),
                maxLines = 1
            )
        }
    }
}

/** Places the element with its horizontal centre on [x], but not left of 0. */
private fun Modifier.centeredAtX(x: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    layout(placeable.width, placeable.height) {
        placeable.place((x.roundToPx() - placeable.width / 2).coerceAtLeast(0), 0)
    }
}

/** A drawn programme bar at [x], [width] wide, inside the row's timeline. */
private data class TvBarSlot(val bar: MultiEpgBar, val x: Dp, val width: Dp)

/**
 * One channel's row: the programme bars and "No information" fillers for the holes in
 * the data, the now-line over the bars, and the bar text over the now-line.
 */
@Composable
private fun TvMultiEpgChannelTimeline(
    channel: MultiEpgChannel,
    timelineStartSec: Long,
    timelineEndSec: Long,
    timelineWidth: Dp,
    minuteWidth: Dp,
    nowSec: Long,
    cullStartSec: Long,
    cullEndSec: Long,
    cursorStartSec: Long?,
    cursorFocused: Boolean,
    viewportStartPx: () -> Int,
    timerClocks: Map<String, MultiEpgTimerClock>,
    rowHeight: Dp,
    eventStyle: TextStyle,
    clockSize: Dp,
    selectedRow: Boolean = false
) {
    val scheme = PhoneMaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val timeStyle = PhoneMaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val secondLineFits = with(density) {
        eventStyle.lineHeight.isSp &&
            timeStyle.lineHeight.isSp &&
            eventStyle.lineHeight.toDp() + timeStyle.lineHeight.toDp() + BarGap * 2 <= rowHeight
    }
    val timeFormat = remember {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
    }
    val noInformation = stringResource(R.string.multiepg_no_information)
    val minuteWidthDp = minuteWidth.value
    val slots = remember(channel.bars, cullStartSec, cullEndSec, timelineStartSec, minuteWidthDp) {
        channel.bars.overlapping(cullStartSec, cullEndSec).mapNotNull { bar ->
            if (bar.endSec <= timelineStartSec) {
                return@mapNotNull null
            }
            val width = MultiEpgBarLayout.widthDp(
                startSec = bar.startSec,
                endSec = bar.endSec,
                timelineStartSec = timelineStartSec,
                minuteWidthDp = minuteWidthDp,
                nextStartSec = MultiEpgBarLayout.nextStartSec(channel.bars, bar.startSec)
            )
            if (width <= 0f) {
                return@mapNotNull null
            }
            val x = MultiEpgBarLayout.offsetDp(bar.startSec, timelineStartSec, minuteWidthDp)
            TvBarSlot(bar, x.dp, width.dp)
        }
    }
    val gaps =
        remember(
            channel.bars,
            cullStartSec,
            cullEndSec,
            timelineStartSec,
            timelineEndSec,
            minuteWidthDp
        ) {
            MultiEpgBarLayout.gaps(
                sortedBars = channel.bars,
                fromSec = maxOf(timelineStartSec, cullStartSec),
                toSec = minOf(timelineEndSec, cullEndSec),
                minGapSec = NO_INFO_MIN_GAP_SEC
            ).map { gap ->
                val x = MultiEpgBarLayout.offsetDp(gap.first, timelineStartSec, minuteWidthDp)
                x.dp to (minuteWidthDp * ((gap.last + 1 - gap.first) / 60f)).dp
            }
        }

    Box(
        modifier = Modifier
            .width(timelineWidth)
            .height(rowHeight)
            .then(if (selectedRow) Modifier.background(tvMultiEpgRowBand()) else Modifier)
    ) {
        for ((x, width) in gaps) {
            TvBarBackground(
                x = x,
                width = width,
                color = scheme.surfaceVariant.copy(alpha = 0.35f),
                shape = shape,
                modifier = Modifier.testTag("tv_multi_epg_no_info")
            )
        }
        for (slot in slots) {
            key(slot.bar.event.eventId, slot.bar.startSec) {
                val cursor = slot.bar.startSec == cursorStartSec
                TvBarBackground(
                    x = slot.x,
                    width = slot.width,
                    color = when {
                        cursor && cursorFocused -> scheme.inverseSurface
                        cursor -> scheme.primaryContainer
                        else -> scheme.surfaceVariant
                    },
                    shape = shape,
                    border = if (cursor && cursorFocused) scheme.primary else null
                )
            }
        }
        if (nowSec in timelineStartSec until timelineEndSec) {
            val nowMin = (nowSec - timelineStartSec) / 60f
            Box(
                modifier = Modifier
                    .offset(x = minuteWidth * nowMin - NowLineWidth / 2)
                    .width(NowLineWidth)
                    .fillMaxHeight()
                    .background(scheme.primary)
            )
        }
        for ((x, width) in gaps) {
            TvBarText(
                x = x,
                width = width,
                title = noInformation,
                secondLine = null,
                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                titleStyle = eventStyle,
                secondStyle = timeStyle,
                endReserve = 0.dp,
                viewportStartPx = viewportStartPx
            )
        }
        for (slot in slots) {
            key(slot.bar.event.eventId, slot.bar.startSec) {
                val bar = slot.bar
                val cursor = bar.startSec == cursorStartSec
                val clock = timerClocks[
                    multiEpgTimerClockKey(channel.serviceRef, bar.event.eventId, bar.startSec)
                ]
                val secondLine = if (secondLineFits && slot.width >= BarSecondLineMinWidth) {
                    val start = timeFormat.format(Date(bar.startSec * 1000L))
                    val end = timeFormat.format(Date(bar.endSec * 1000L))
                    "$start – $end"
                } else {
                    null
                }
                TvBarText(
                    x = slot.x,
                    width = slot.width,
                    title = bar.event.title,
                    secondLine = secondLine,
                    color = when {
                        cursor && cursorFocused -> scheme.inverseOnSurface
                        cursor -> scheme.onPrimaryContainer
                        else -> scheme.onSurfaceVariant
                    },
                    titleStyle = eventStyle,
                    secondStyle = timeStyle,
                    endReserve = if (clock != null) clockSize + 2.dp else 0.dp,
                    viewportStartPx = viewportStartPx
                )
                if (clock != null) {
                    TvBarClock(
                        x = slot.x,
                        width = slot.width,
                        clock = clock,
                        clockSize = clockSize
                    )
                }
            }
        }
    }
}

@Composable
private fun TvBarBackground(
    x: Dp,
    width: Dp,
    color: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    border: Color? = null
) {
    Box(
        modifier = modifier
            .offset(x = x)
            .width(width)
            .fillMaxHeight()
            .padding(BarGap / 2)
            .background(color, shape)
            .then(
                if (border != null) {
                    Modifier.border(FocusBorderWidth, border, shape)
                } else {
                    Modifier
                }
            )
    )
}

/**
 * A bar's title (and [secondLine], if any). Bars narrower than [BarMinTextWidth] show no
 * text; the text of a bar that began left of the viewport sticks to the viewport's edge.
 */
@Composable
private fun TvBarText(
    x: Dp,
    width: Dp,
    title: String,
    secondLine: String?,
    color: Color,
    titleStyle: TextStyle,
    secondStyle: TextStyle,
    endReserve: Dp,
    viewportStartPx: () -> Int
) {
    if (width < BarMinTextWidth) {
        return
    }
    Column(
        modifier = Modifier
            .offset(x = x)
            .width(width)
            .fillMaxHeight()
            .stickyBarText(barStart = x, barWidth = width, viewportStartPx = viewportStartPx)
            .padding(
                start = BarGap / 2 + BarTextPadding,
                end = BarGap / 2 + BarTextPadding + endReserve
            ),
        verticalArrangement = Arrangement.Center
    ) {
        PhoneText(
            text = title,
            style = titleStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (secondLine != null) {
            PhoneText(
                text = secondLine,
                style = secondStyle,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Lays the text out from the viewport's left edge when the bar began before it, and drops
 * it when less than [BarMinTextWidth] of the bar is left. Reads the scroll offset in the
 * layout phase, so panning relayouts the text without recomposing the row.
 */
private fun Modifier.stickyBarText(
    barStart: Dp,
    barWidth: Dp,
    viewportStartPx: () -> Int
): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val viewportStart = viewportStartPx().toDp().value
    val textWidth = MultiEpgBarLayout.textWidthDp(
        barStartDp = barStart.value,
        barWidthDp = barWidth.value,
        viewportStartDp = viewportStart,
        minTextWidthDp = BarMinTextWidth.value
    )
    if (textWidth <= 0f) {
        return@layout layout(width, height) {}
    }
    val inset = MultiEpgBarLayout.stickyTextInsetDp(
        barStart.value,
        barWidth.value,
        viewportStart
    )
    val placeable = measurable.measure(
        Constraints.fixed(textWidth.dp.roundToPx().coerceIn(0, width), height)
    )
    layout(width, height) {
        placeable.place(inset.dp.roundToPx(), 0)
    }
}

@Composable
private fun TvBarClock(x: Dp, width: Dp, clock: MultiEpgTimerClock, clockSize: Dp) {
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
            .offset(x = x)
            .width(width)
            .fillMaxHeight()
            .padding(end = BarGap / 2 + 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(modifier = Modifier.size(clockSize)) {
            PhoneIcon(
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
