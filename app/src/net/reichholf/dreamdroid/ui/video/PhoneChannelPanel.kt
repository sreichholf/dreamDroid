package net.reichholf.dreamdroid.ui.video

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import java.util.Date
import kotlin.math.abs
import kotlin.math.min
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.userBouquetTabs
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceRef
import net.reichholf.dreamdroid.tv.ui.nowEventTimes
import net.reichholf.dreamdroid.tv.ui.rememberMinuteNowMs
import net.reichholf.dreamdroid.tv.ui.tvHubDrawsMarkerHeader
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

const val PHONE_CHANNEL_PANEL_TAG = "phone_channel_panel"
const val PHONE_CHANNEL_ROW_TAG = "phone_channel_row"
const val PHONE_CHANNEL_DETAIL_TAG = "phone_channel_detail"

const val DEFAULT_VIDEO_ASPECT = 16f / 9f

private val PanelShape = RoundedCornerShape(16.dp)
private const val PANEL_ALPHA = 0.92f
private val RowHeight = 62.dp
private val RowShape = RoundedCornerShape(12.dp)
private val SwipeThreshold = 64.dp
private val TabletPanelStart = 32.dp
private val TabletPanelWidth = 400.dp

/** Rows kept above the playing channel when a player channel list scrolls to it. */
internal const val CHANNEL_LIST_CONTEXT_ROWS = 2

/** Where the player channel list sits: below the docked video, or over it on the left. */
enum class PhoneChannelLayout { Portrait, Landscape, Tablet }

fun phoneChannelLayout(sizeClass: WindowSizeClass): PhoneChannelLayout = when {
    !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
        PhoneChannelLayout.Landscape

    sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
        PhoneChannelLayout.Tablet

    else -> PhoneChannelLayout.Portrait
}

/** Controller-owned panel inputs that are not receiver data. */
class PhoneChannelPanelState {
    /** Width / height of the picture; portrait docks the video above the list at this shape. */
    var videoAspect by mutableFloatStateOf(DEFAULT_VIDEO_ASPECT)

    /** Bumped on every open, so the list scrolls back to the playing channel. */
    var openCount by mutableIntStateOf(0)
}

/**
 * User-bouquet names for the stream overlay, TV first then radio.
 * Provider roots and the aggregate index are dropped.
 */
fun overlayBouquets(tv: List<Service>, radio: List<Service>, excluded: Set<String>): List<Service> {
    val tvTabs = userBouquetTabs(tv, excluded)
    val radioTabs = userBouquetTabs(radio, excluded)
    return (tvTabs + radioTabs).distinctBy { it.reference }
}

/** The first row to show so the playing channel has context above it; null without one. */
internal fun zapScrollTarget(refs: List<String>, currentRef: String?): Int? {
    if (currentRef == null || ServiceRef.isMarker(currentRef)) {
        return null
    }
    val index = refs.indexOf(currentRef).takeIf { it >= 0 } ?: return null
    return (index - CHANNEL_LIST_CONTEXT_ROWS).coerceAtLeast(0)
}

/** Height of a picture of [aspect] fitted into a [width] × [height] box. */
internal fun fittedVideoHeight(width: Float, height: Float, aspect: Float): Float =
    if (aspect > 0f) min(height, width / aspect) else height

/** Moves a centred picture of [aspect] up to the top edge of its [width] × [height] container. */
fun videoTopTranslation(width: Int, height: Int, aspect: Float): Float =
    -(height - fittedVideoHeight(width.toFloat(), height.toFloat(), aspect)) / 2f

/**
 * Player channel list for phones and tablets: the shown bouquet with previous / next in the
 * header (a horizontal swipe on the panel does the same), and the playing channel's details
 * beside it on [PhoneChannelLayout.Tablet].
 */
@Composable
fun PhoneChannelPanel(
    services: List<ServiceNowNext>,
    currentRef: String?,
    bouquets: List<Service>,
    selectedBouquetRef: String?,
    layout: PhoneChannelLayout,
    onServiceClick: (ServiceNowNext) -> Unit,
    onSelectBouquet: (Service) -> Unit,
    modifier: Modifier = Modifier,
    videoAspect: Float = DEFAULT_VIDEO_ASPECT,
    scrollKey: Any? = null,
    nowMs: Long = rememberMinuteNowMs(),
    onScrollInProgress: ((Boolean) -> Unit)? = null
) {
    val rows =
        remember(services) { services.filterNot { ServiceRef.isSpacer(it.serviceReference) } }
    val refs = remember(rows) { rows.map { it.serviceReference } }
    val listState = rememberLazyListState()
    LaunchedEffect(refs, currentRef, scrollKey, selectedBouquetRef) {
        listState.scrollToItem(zapScrollTarget(refs, currentRef) ?: 0)
    }
    if (onScrollInProgress != null) {
        val report by rememberUpdatedState(onScrollInProgress)
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { report(it) }
        }
    }

    val selectedIndex = bouquets.indexOfFirst { it.reference == selectedBouquetRef }
    val canSwitch = bouquets.size > 1
    val switchBouquet: (Int) -> Unit = { step ->
        if (canSwitch) {
            val to = if (selectedIndex < 0) {
                if (step > 0) 0 else bouquets.lastIndex
            } else {
                (selectedIndex + step + bouquets.size) % bouquets.size
            }
            onSelectBouquet(bouquets[to])
        }
    }
    val panelColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = PANEL_ALPHA)

    // video_player.xml's root (fitsSystemWindows) already insets the whole player.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelModifier = when (layout) {
            PhoneChannelLayout.Portrait -> {
                val videoHeight =
                    fittedVideoHeight(maxWidth.value, maxHeight.value, videoAspect).dp
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = videoHeight + 12.dp, bottom = 24.dp)
            }

            PhoneChannelLayout.Landscape ->
                Modifier
                    .padding(start = 24.dp, top = 12.dp, bottom = 12.dp)
                    .width(340.dp)
                    .fillMaxHeight()

            PhoneChannelLayout.Tablet ->
                Modifier
                    .padding(start = TabletPanelStart, top = 24.dp, bottom = 24.dp)
                    .width(TabletPanelWidth)
                    .fillMaxHeight()
        }
        ChannelPanel(
            rows = rows,
            currentRef = currentRef,
            bouquetName = bouquets.getOrNull(selectedIndex)?.name
                ?: stringResource(R.string.bouquets),
            bouquetPosition = "${selectedIndex + 1} / ${bouquets.size}"
                .takeIf { selectedIndex >= 0 },
            canSwitch = canSwitch,
            onSwitchBouquet = switchBouquet,
            onServiceClick = onServiceClick,
            listState = listState,
            nowMs = nowMs,
            compactHeader = layout == PhoneChannelLayout.Landscape,
            listHorizontalPadding = if (layout == PhoneChannelLayout.Tablet) 12.dp else 8.dp,
            background = panelColor,
            modifier = panelModifier
        )
        val playing = rows.firstOrNull { it.serviceReference == currentRef }
        if (layout == PhoneChannelLayout.Tablet && playing != null) {
            ChannelDetailCard(
                service = playing,
                nowMs = nowMs,
                background = panelColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        start = TabletPanelStart + TabletPanelWidth + 32.dp,
                        end = 32.dp,
                        bottom = 24.dp
                    )
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChannelPanel(
    rows: List<ServiceNowNext>,
    currentRef: String?,
    bouquetName: String,
    bouquetPosition: String?,
    canSwitch: Boolean,
    onSwitchBouquet: (Int) -> Unit,
    onServiceClick: (ServiceNowNext) -> Unit,
    listState: LazyListState,
    nowMs: Long,
    compactHeader: Boolean,
    listHorizontalPadding: Dp,
    background: Color,
    modifier: Modifier = Modifier
) {
    val switch by rememberUpdatedState(onSwitchBouquet)
    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(background)
            .testTag(PHONE_CHANNEL_PANEL_TAG)
            .pointerInput(canSwitch) {
                if (!canSwitch) {
                    return@pointerInput
                }
                val threshold = SwipeThreshold.toPx()
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                    },
                    onDragEnd = {
                        if (abs(dragged) >= threshold) {
                            switch(if (dragged < 0f) 1 else -1)
                        }
                    }
                )
            }
    ) {
        ChannelPanelHeader(
            name = bouquetName,
            position = bouquetPosition,
            canSwitch = canSwitch,
            onSwitchBouquet = onSwitchBouquet,
            compact = compactHeader
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = listHorizontalPadding, vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(rows, key = { index, it -> "$index|${it.serviceReference}" }) { _, row ->
                if (tvHubDrawsMarkerHeader(row.serviceReference)) {
                    ChannelMarker(row.serviceName)
                } else {
                    ChannelRow(
                        service = row,
                        playing = row.serviceReference == currentRef,
                        nowMs = nowMs,
                        onClick = { onServiceClick(row) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelPanelHeader(
    name: String,
    position: String?,
    canSwitch: Boolean,
    onSwitchBouquet: (Int) -> Unit,
    compact: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (compact) 4.dp else 8.dp)
    ) {
        BouquetChevron(
            iconRes = R.drawable.ic_chevron_left,
            label = stringResource(R.string.previous_bouquet),
            visible = canSwitch,
            onClick = { onSwitchBouquet(-1) }
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() }
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (position != null) {
                Text(
                    text = position,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        BouquetChevron(
            iconRes = R.drawable.ic_chevron_right,
            label = stringResource(R.string.next_bouquet),
            visible = canSwitch,
            onClick = { onSwitchBouquet(1) }
        )
    }
}

@Composable
private fun BouquetChevron(iconRes: Int, label: String, visible: Boolean, onClick: () -> Unit) {
    if (!visible) {
        Spacer(modifier = Modifier.size(48.dp))
        return
    }
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = label)
    }
}

@Composable
private fun ChannelMarker(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@Composable
private fun ChannelRow(
    service: ServiceNowNext,
    playing: Boolean,
    nowMs: Long,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val progress = eventProgress(service.now, nowMs)
    val nowTitle = service.now?.title.orEmpty()
    Surface(
        onClick = onClick,
        shape = RowShape,
        color = if (playing) colors.secondaryContainer else Color.Transparent,
        contentColor = if (playing) colors.onSecondaryContainer else colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .testTag(PHONE_CHANNEL_ROW_TAG)
            .semantics {
                role = Role.Button
                selected = playing
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            PiconImage(
                reference = service.serviceReference,
                name = service.serviceName,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(width = 56.dp, height = 34.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = service.serviceName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playing) {
                        PlayingTag(modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (nowTitle.isNotEmpty()) {
                    Text(
                        text = nowTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress != null) {
                    EventProgressBar(progress = progress, height = 3.dp)
                }
            }
        }
    }
}

@Composable
private fun EventProgressBar(progress: EventProgress, height: Dp, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.elapsedSec.toFloat() / progress.totalSec.toFloat() },
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
private fun PlayingTag(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.tv_now_playing).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun ChannelDetailCard(
    service: ServiceNowNext,
    nowMs: Long,
    background: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val now = service.now
    val next = service.next
    val progress = eventProgress(now, nowMs)
    val times = nowEventTimes(context, now, nowMs)
    val clock = remember(nowMs) { DateFormat.getTimeFormat(context).format(Date(nowMs)) }
    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(background)
            .testTag(PHONE_CHANNEL_DETAIL_TAG)
    ) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.elapsedSec.toFloat() / progress.totalSec.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = colors.primary,
                trackColor = colors.onSurface.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PiconImage(
                    reference = service.serviceReference,
                    name = service.serviceName,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 96.dp, height = 58.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = service.serviceName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (times != null) {
                            Text(
                                text = times,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                    val nowTitle = now?.title.orEmpty()
                    if (nowTitle.isNotEmpty()) {
                        Text(
                            text = nowTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    if (next != null && next.title.isNotEmpty()) {
                        Text(
                            text = buildAnnotatedString {
                                if (next.startTimeReadable.isNotEmpty()) {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                        append(next.startTimeReadable)
                                    }
                                    append("  ")
                                }
                                append(next.title)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Text(
                    text = clock,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Top)
                )
            }
            val description = now?.description.orEmpty()
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun ComposeView.bindPhoneChannelPanel(
    state: VideoOverlayUiState,
    panel: PhoneChannelPanelState,
    onServiceClick: (ServiceNowNext) -> Unit,
    onSelectBouquet: (Service) -> Unit,
    onLayout: (PhoneChannelLayout) -> Unit,
    onScrollInProgress: (Boolean) -> Unit
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme(forceDark = true) {
            val layout = phoneChannelLayout(currentWindowAdaptiveInfoV2().windowSizeClass)
            LaunchedEffect(layout) { onLayout(layout) }
            PhoneChannelPanel(
                services = state.zapServices,
                currentRef = state.zapCurrentRef,
                bouquets = state.bouquets,
                selectedBouquetRef = state.selectedBouquetRef,
                layout = layout,
                onServiceClick = onServiceClick,
                onSelectBouquet = onSelectBouquet,
                videoAspect = panel.videoAspect,
                scrollKey = panel.openCount,
                onScrollInProgress = onScrollInProgress
            )
        }
    }
}
