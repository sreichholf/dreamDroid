package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceRef
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.theme.dreamDroidTvCardColors
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState
import net.reichholf.dreamdroid.ui.video.eventProgress

const val TV_CHANNEL_LIST_TAG = "tv_channel_list"
const val TV_CHANNEL_ROW_TAG = "tv_channel_row"
const val TV_CHANNEL_DETAIL_TAG = "tv_channel_detail"

private val PanelWidth = 400.dp
private val PanelShape = RoundedCornerShape(16.dp)
private const val PANEL_ALPHA = 0.94f
private val RowHeight = 62.dp
private val RowShape = RoundedCornerShape(12.dp)

/** Rows kept above the target when the list scrolls to it, so it does not sit at the edge. */
private const val SCROLL_CONTEXT_ROWS = 2

/**
 * Player channel list: the shown bouquet as a vertical list docked left, Left/Right switching
 * bouquets, and the focused channel's now/next in a card at the bottom end.
 * [focusRequester] lands on the playing channel, else on the first one.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelList(
    services: List<ServiceNowNext>,
    currentRef: String?,
    bouquets: List<Service>,
    selectedBouquetRef: String?,
    onServiceClick: (ServiceNowNext) -> Unit,
    onSelectBouquet: (Service) -> Unit,
    modifier: Modifier = Modifier,
    nowMs: Long = rememberMinuteNowMs(),
    focusRequester: FocusRequester? = null,
    onUserInteraction: (() -> Unit)? = null,
    onScrollInProgress: ((Boolean) -> Unit)? = null
) {
    val rows =
        remember(services) { services.filterNot { ServiceRef.isSpacer(it.serviceReference) } }
    val refs = remember(rows) { rows.map { it.serviceReference } }
    val targetIndex = remember(refs, currentRef) {
        refs.indexOf(currentRef).takeIf { it >= 0 && !ServiceRef.isMarker(currentRef) }
            ?: refs.indexOfFirst { !tvHubDrawsMarkerHeader(it) }
    }
    val ownRequester = remember { FocusRequester() }
    val targetRequester = focusRequester ?: ownRequester
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listHasFocus by remember { mutableStateOf(false) }
    var refocusPending by remember { mutableStateOf(false) }
    var focusedRef by remember { mutableStateOf<String?>(null) }

    suspend fun scrollToTarget() {
        if (targetIndex >= 0) {
            listState.scrollToItem((targetIndex - SCROLL_CONTEXT_ROWS).coerceAtLeast(0))
        }
    }

    // Keyed on the references, not the events: the controller refreshes now/next while the
    // user browses, and that must not move focus.
    LaunchedEffect(refs, currentRef) {
        scrollToTarget()
        if (targetIndex >= 0 && (refocusPending || listHasFocus)) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            }.first { it }
            refocusPending = false
            try {
                targetRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }
    if (onScrollInProgress != null) {
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { onScrollInProgress(it) }
        }
    }

    val selectedIndex = bouquets.indexOfFirst { it.reference == selectedBouquetRef }
    val selectedBouquet = bouquets.getOrNull(selectedIndex)
    val canSwitch = bouquets.size > 1
    val bouquetPosition = "${selectedIndex + 1} / ${bouquets.size}".takeIf { selectedIndex >= 0 }

    fun switchBouquet(step: Int) {
        if (!canSwitch) {
            return
        }
        val from = selectedIndex.coerceAtLeast(0)
        refocusPending = true
        onSelectBouquet(bouquets[(from + step + bouquets.size) % bouquets.size])
    }

    val phone = PhoneMaterialTheme.colorScheme
    val panelColor = phone.surfaceContainerLow.copy(alpha = PANEL_ALPHA)
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(PanelWidth)
                .fillMaxHeight()
                .clip(PanelShape)
                .background(panelColor)
                .testTag(TV_CHANNEL_LIST_TAG)
                .onPreviewKeyEvent { event ->
                    onUserInteraction?.invoke()
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight -> {
                            // Holding the key repeats KeyDown, and each switch reloads the
                            // bouquet and the stream.
                            val firstPress = event.nativeKeyEvent.repeatCount == 0
                            if (event.type == KeyEventType.KeyDown && firstPress) {
                                switchBouquet(if (event.key == Key.DirectionRight) 1 else -1)
                            }
                            true
                        }

                        else -> false
                    }
                }
        ) {
            ChannelListHeader(
                name = selectedBouquet?.name.orEmpty(),
                position = bouquetPosition,
                showChevrons = canSwitch
            )
            HorizontalDivider(color = phone.outlineVariant)
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                // Room for the focus scale, so the focused row keeps its corners.
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { state ->
                        val lost = listHasFocus && !state.hasFocus
                        listHasFocus = state.hasFocus
                        if (lost) {
                            // The requester only works while its row is composed; the next
                            // open starts at the playing channel again.
                            scope.launch { scrollToTarget() }
                        }
                    }
            ) {
                itemsIndexed(rows, key = { index, it ->
                    "$index|${it.serviceReference}"
                }) { index, service ->
                    if (tvHubDrawsMarkerHeader(service.serviceReference)) {
                        ChannelMarker(service.serviceName)
                    } else {
                        ChannelRow(
                            service = service,
                            playing = service.serviceReference == currentRef,
                            nowMs = nowMs,
                            onClick = { onServiceClick(service) },
                            modifier = Modifier
                                .then(
                                    if (index == targetIndex) {
                                        Modifier.focusRequester(targetRequester)
                                    } else {
                                        Modifier
                                    }
                                )
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        focusedRef = service.serviceReference
                                        onUserInteraction?.invoke()
                                    } else if (focusedRef == service.serviceReference) {
                                        focusedRef = null
                                    }
                                }
                        )
                    }
                }
            }
        }
        val focused = rows.firstOrNull { it.serviceReference == focusedRef }
        if (focused != null) {
            ChannelDetailCard(
                service = focused,
                nowMs = nowMs,
                background = panelColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = PanelWidth + 32.dp)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelListHeader(name: String, position: String?, showChevrons: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        ChevronSlot(R.drawable.ic_chevron_left, showChevrons)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
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
                    color = PhoneMaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ChevronSlot(R.drawable.ic_chevron_right, showChevrons)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChevronSlot(iconRes: Int, visible: Boolean) {
    if (visible) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = PhoneMaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    } else {
        Spacer(modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelMarker(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = PhoneMaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelRow(
    service: ServiceNowNext,
    playing: Boolean,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = eventProgress(service.now, nowMs)
    val nowTitle = service.now?.title.orEmpty()
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .testTag(TV_CHANNEL_ROW_TAG)
            .semantics {
                role = Role.Button
                selected = playing
            },
        colors = if (playing) {
            dreamDroidTvCardColors(
                restContainer = PhoneMaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            dreamDroidTvCardColors()
        },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        shape = ClickableSurfaceDefaults.shape(shape = RowShape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        ) {
            PiconImage(
                reference = service.serviceReference,
                name = service.serviceName,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(width = 56.dp, height = 34.dp)
            )
            Column(
                verticalArrangement = Arrangement.Center,
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress != null) {
                    val content = LocalContentColor.current
                    LinearProgressIndicator(
                        progress = { progress.elapsedSec.toFloat() / progress.totalSec.toFloat() },
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = content,
                        trackColor = content.copy(alpha = 0.24f),
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayingTag(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.tv_now_playing).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = modifier
            .background(LocalContentColor.current.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelDetailCard(
    service: ServiceNowNext,
    nowMs: Long,
    background: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val phone = PhoneMaterialTheme.colorScheme
    val now = service.now
    val next = service.next
    val progress = eventProgress(now, nowMs)
    val times = nowEventTimes(context, now, nowMs)
    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(background)
            .testTag(TV_CHANNEL_DETAIL_TAG)
    ) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.elapsedSec.toFloat() / progress.totalSec.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = phone.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = service.serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (times != null) {
                    Text(
                        text = times,
                        style = MaterialTheme.typography.bodyMedium,
                        color = phone.onSurfaceVariant,
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
                    overflow = TextOverflow.Ellipsis
                )
            }
            val description = now?.description.orEmpty()
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = phone.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (next != null && next.title.isNotEmpty()) {
                Text(
                    text = listOf(next.startTimeReadable, next.title)
                        .filter { it.isNotEmpty() }
                        .joinToString("  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = phone.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun ComposeView.bindTvChannelList(
    state: VideoOverlayUiState,
    onServiceClick: (ServiceNowNext) -> Unit,
    onSelectBouquet: (Service) -> Unit,
    onUserInteraction: (() -> Unit)? = null,
    onScrollInProgress: ((Boolean) -> Unit)? = null
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    // View focus (from the controller) is forwarded into the target row.
    isFocusable = true
    isFocusableInTouchMode = true
    val targetFocus = FocusRequester()
    setContent {
        DreamDroidTvTheme(fillBackground = false, forceDark = true) {
            TvChannelList(
                services = state.zapServices,
                currentRef = state.zapCurrentRef,
                bouquets = state.bouquets,
                selectedBouquetRef = state.selectedBouquetRef,
                onServiceClick = onServiceClick,
                onSelectBouquet = onSelectBouquet,
                focusRequester = targetFocus,
                onUserInteraction = onUserInteraction,
                onScrollInProgress = onScrollInProgress
            )
        }
    }
    setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            post {
                try {
                    targetFocus.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
        }
    }
}
