package net.reichholf.dreamdroid.ui.video

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.tv.ui.TvPlayerEpgDetailDialog
import net.reichholf.dreamdroid.tv.ui.TvPlayerMovieDetailDialog
import net.reichholf.dreamdroid.tv.ui.nowEventTimes
import net.reichholf.dreamdroid.tv.ui.rememberMinuteNowMs
import net.reichholf.dreamdroid.ui.dialogs.SimpleChoiceAlertDialog
import net.reichholf.dreamdroid.ui.epg.EpgDetailContent
import net.reichholf.dreamdroid.ui.epg.EpgDetailModalSheet
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.movies.MovieDetailModalSheet
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.video.PlayerState

const val VIDEO_OVERLAY_PANEL_TAG = "video_overlay_panel"
const val VIDEO_OVERLAY_PROGRESS_TAG = "video_overlay_progress"
const val VIDEO_OVERLAY_CLOCK_TAG = "video_overlay_clock"
const val VIDEO_OVERLAY_SLEEP_BADGE_TAG = "video_overlay_sleep_badge"

/** How long the Slider shows a seek target that playback progress never reaches. */
internal const val SEEK_SETTLE_TIMEOUT_MS = 5_000L

private const val PANEL_ALPHA = 0.92f
private val PanelShape = RoundedCornerShape(16.dp)
private val ButtonSize = 48.dp
private val TransportPlaySize = 64.dp
private val ButtonIconSize = 22.dp

/** Panel width below which landscape moves the live actions under the programme. */
private val LandscapeOneRowMinWidth = 700.dp

/** Where the overlay lays out its panels, from the window size class. */
enum class VideoOverlayLayout {
    Portrait,

    /** Compact height: a phone in landscape. */
    Landscape,

    /** Expanded width with room in height: a tablet. */
    Tablet
}

@Composable
fun videoOverlayLayout(): VideoOverlayLayout {
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
            VideoOverlayLayout.Landscape

        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            VideoOverlayLayout.Tablet

        else -> VideoOverlayLayout.Portrait
    }
}

/**
 * Mutable overlay chrome state driven by [VideoOverlayController].
 */
class VideoOverlayUiState {
    var title by mutableStateOf("")

    /** Anything but a live service: the panel shows the seek bar instead of the programme. */
    var recording by mutableStateOf(false)

    /** A recording's "service · date". */
    var subtitle by mutableStateOf("")

    /** A recording's time left, "−mm:ss". */
    var remaining by mutableStateOf("")
    var nowStart by mutableStateOf("")
    var nowTitle by mutableStateOf("")
    var nowDuration by mutableStateOf("")
    var showNow by mutableStateOf(false)
    var nextStart by mutableStateOf("")
    var nextTitle by mutableStateOf("")
    var nextDuration by mutableStateOf("")
    var hasNext by mutableStateOf(false)

    /** Live TV: the running event behind the now/next strings, for the TV infobar's times. */
    var nowEvent by mutableStateOf<Event?>(null)
    var showPvrControls by mutableStateOf(false)
    var progressMax by mutableIntStateOf(0)
    var progress by mutableIntStateOf(0)
    var progressEnabled by mutableStateOf(false)
    var seekable by mutableStateOf(false)

    /** Playing forward, so the play/pause button pauses; false while paused or rewinding. */
    var isPlaying by mutableStateOf(false)
    var showAudioButton by mutableStateOf(false)
    var showSubtitleButton by mutableStateOf(false)
    var showListButton by mutableStateOf(false)
    var showInfoButton by mutableStateOf(false)
    var sleepTimer by mutableStateOf<SleepTimer>(SleepTimer.Off)

    /** Zap rows for the Compose channel list (phone column and TV cards). */
    var zapServices by mutableStateOf<List<ServiceNowNext>>(emptyList())
    var zapCurrentRef by mutableStateOf<String?>(null)

    /** Bouquet names shown above the channel list while a live stream is open. */
    var bouquets by mutableStateOf<List<Service>>(emptyList())
    var selectedBouquetRef by mutableStateOf<String?>(null)

    /** The open detail sheet: an EPG event or a movie, at most one. */
    var epgDetailContent by mutableStateOf<EpgDetailContent?>(null)
    var movieDetailContent by mutableStateOf<MovieDetailContent?>(null)

    /** The open choice dialog (audio or subtitle track, sleep timer); null title when closed. */
    var choiceTitle by mutableStateOf<String?>(null)
    var choiceItems by mutableStateOf<List<String>>(emptyList())
    var choiceActionIds by mutableStateOf<IntArray>(intArrayOf())
    var choiceDialogTag by mutableStateOf<String?>(null)
    var onChoiceAction: ((actionId: Int, dialogTag: String) -> Unit)? = null

    fun showChrome(chrome: OverlayChrome) {
        title = chrome.title
        recording = chrome.recording
        subtitle = chrome.subtitle.orEmpty()
        remaining = chrome.remaining.orEmpty()
        showInfoButton = chrome.showInfoButton
        showPvrControls = chrome.showPvrControls
        seekable = chrome.seekable
        nowStart = chrome.now?.start.orEmpty()
        nowTitle = chrome.now?.title.orEmpty()
        nowDuration = chrome.now?.duration.orEmpty()
        showNow = chrome.now != null
        nowEvent = chrome.nowEvent
        nextStart = chrome.next?.start.orEmpty()
        nextTitle = chrome.next?.title.orEmpty()
        nextDuration = chrome.next?.duration.orEmpty()
        hasNext = chrome.next != null
        progressEnabled = chrome.progress != null
        progressMax = chrome.progress?.max ?: 0
        progress = chrome.progress?.position ?: 0
    }

    fun showChoice(title: String, items: List<String>, actionIds: IntArray, dialogTag: String) {
        choiceTitle = title
        choiceItems = items
        choiceActionIds = actionIds
        choiceDialogTag = dialogTag
    }

    fun dismissChoice() {
        choiceTitle = null
        choiceItems = emptyList()
        choiceActionIds = intArrayOf()
        choiceDialogTag = null
    }
}

/** What the play/pause button shows: Pause only while play/pause would pause. */
val PlayerState.playingForward: Boolean get() = isPlaying && !rewinding

@Immutable
private class InfobarCallbacks(
    val onPlay: () -> Unit,
    val onRewind: () -> Unit,
    val onForward: () -> Unit,
    val onInfo: () -> Unit,
    val onList: () -> Unit,
    val onAudio: () -> Unit,
    val onSubtitle: () -> Unit,
    val onSleepTimer: () -> Unit,
    val onSeekChange: (Int) -> Unit
)

/**
 * The phone and tablet infobar: one floating panel at the bottom with the channel and its
 * programme (live) or the recording and its seek bar, and round actions. [layout] picks the
 * portrait, landscape or tablet arrangement.
 */
@Composable
fun VideoOverlayScreen(
    state: VideoOverlayUiState,
    onPlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onInfo: () -> Unit,
    onList: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onSleepTimer: () -> Unit,
    onSeekChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    layout: VideoOverlayLayout = videoOverlayLayout(),
    nowMs: Long = rememberMinuteNowMs(),
    firstControlFocusRequester: FocusRequester? = null
) {
    val callbacks = InfobarCallbacks(
        onPlay = onPlay,
        onRewind = onRewind,
        onForward = onForward,
        onInfo = onInfo,
        onList = onList,
        onAudio = onAudio,
        onSubtitle = onSubtitle,
        onSleepTimer = onSleepTimer,
        onSeekChange = onSeekChange
    )
    val (outer, maxWidth) = when (layout) {
        VideoOverlayLayout.Portrait -> PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp) to
            600.dp

        VideoOverlayLayout.Landscape ->
            PaddingValues(start = 48.dp, end = 48.dp, bottom = 16.dp) to Dp.Unspecified

        VideoOverlayLayout.Tablet ->
            PaddingValues(start = 32.dp, end = 32.dp, bottom = 24.dp) to 720.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(outer),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .clip(PanelShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = PANEL_ALPHA))
                .testTag(VIDEO_OVERLAY_PANEL_TAG)
                .focusGroup()
        ) {
            if (state.recording) {
                RecordingInfobar(state, layout, nowMs, callbacks, firstControlFocusRequester)
            } else {
                if (state.progressMax > 0) {
                    LiveProgressEdge(state)
                }
                LiveInfobar(state, layout, nowMs, callbacks, firstControlFocusRequester)
            }
        }
    }
}

/**
 * Back and picture-in-picture over the top corners; PiP only where [onPictureInPicture] is set.
 * While [channelListOpen] the list takes the top: a tablet keeps PiP alone, phones show neither.
 */
@Composable
fun VideoOverlayTopBar(
    onBack: () -> Unit,
    onPictureInPicture: (() -> Unit)?,
    modifier: Modifier = Modifier,
    channelListOpen: Boolean = false,
    layout: VideoOverlayLayout = videoOverlayLayout()
) {
    if (channelListOpen && layout != VideoOverlayLayout.Tablet) {
        return
    }
    val (horizontal, top) = when (layout) {
        VideoOverlayLayout.Portrait -> 12.dp to 16.dp
        VideoOverlayLayout.Landscape -> 24.dp to 16.dp
        VideoOverlayLayout.Tablet -> 32.dp to 24.dp
    }
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = PANEL_ALPHA),
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = horizontal, end = horizontal, top = top),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!channelListOpen) {
            OverlayButton(
                iconRes = R.drawable.ic_arrow_back,
                label = stringResource(R.string.player_back),
                onClick = onBack,
                colors = colors,
                iconSize = 24.dp
            )
        }
        Spacer(Modifier.weight(1f))
        if (onPictureInPicture != null) {
            OverlayButton(
                iconRes = R.drawable.ic_picture_in_picture,
                label = stringResource(R.string.player_picture_in_picture),
                onClick = onPictureInPicture,
                colors = colors,
                iconSize = 24.dp
            )
        }
    }
}

@Composable
private fun LiveInfobar(
    state: VideoOverlayUiState,
    layout: VideoOverlayLayout,
    nowMs: Long,
    callbacks: InfobarCallbacks,
    playFocus: FocusRequester?
) {
    val context = LocalContext.current
    val times = nowEventTimes(context, state.nowEvent, nowMs) ?: state.nowStart
    when (layout) {
        VideoOverlayLayout.Portrait -> Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LivePicon(state, width = 64.dp, height = 38.dp, corner = 6.dp)
                Column(modifier = Modifier.weight(1f)) {
                    PanelTitle(state.title, 18.sp)
                    SecondaryLine(times, 13.sp)
                }
                InfobarClock(nowMs, 16.sp, Modifier.align(Alignment.Top))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NowTitle(state.nowTitle, 16.sp)
                NextLine(state, 14.sp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ActionButtons(state, callbacks, playFocus, withPlay = true)
            }
        }

        VideoOverlayLayout.Landscape -> BoxWithConstraints {
            val oneRow = maxWidth >= LandscapeOneRowMinWidth
            Column(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LivePicon(state, width = 80.dp, height = 48.dp, corner = 8.dp)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TitleWithTimes(state.title, 18.sp, times, 13.sp)
                        NowTitle(state.nowTitle, 15.sp)
                        NextLine(state, 13.sp)
                    }
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InfobarClock(nowMs, 16.sp)
                        if (oneRow) {
                            Row(
                                modifier = Modifier.focusGroup(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButtons(state, callbacks, playFocus, withPlay = true)
                            }
                        }
                    }
                }
                if (!oneRow) {
                    Row(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionButtons(state, callbacks, playFocus, withPlay = true)
                    }
                }
            }
        }

        VideoOverlayLayout.Tablet -> Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LivePicon(state, width = 96.dp, height = 58.dp, corner = 8.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TitleWithTimes(state.title, 20.sp, times, 14.sp)
                    NowTitle(state.nowTitle, 16.sp)
                    NextLine(state, 14.sp)
                }
                InfobarClock(nowMs, 18.sp, Modifier.align(Alignment.Top))
            }
            val description = state.nowEvent?.description.orEmpty()
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButtons(state, callbacks, playFocus, withPlay = true)
            }
        }
    }
}

@Composable
private fun RecordingInfobar(
    state: VideoOverlayUiState,
    layout: VideoOverlayLayout,
    nowMs: Long,
    callbacks: InfobarCallbacks,
    playFocus: FocusRequester?
) {
    if (layout == VideoOverlayLayout.Landscape) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecordingHeader(state, nowMs, 18.sp)
            SeekBar(state, callbacks.onSeekChange)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Transport(state, callbacks, playFocus)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButtons(state, callbacks, playFocus = null, withPlay = false)
                }
            }
        }
        return
    }
    val tablet = layout == VideoOverlayLayout.Tablet
    Column(
        modifier = Modifier.padding(if (tablet) 20.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RecordingHeader(state, nowMs, if (tablet) 20.sp else 18.sp)
        SeekBar(state, callbacks.onSeekChange)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Transport(state, callbacks, playFocus)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            ActionButtons(state, callbacks, playFocus = null, withPlay = false)
        }
    }
}

@Composable
private fun RecordingHeader(state: VideoOverlayUiState, nowMs: Long, titleSize: TextUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            PanelTitle(state.title, titleSize)
            SecondaryLine(state.subtitle, 13.sp)
        }
        InfobarClock(nowMs, 16.sp)
    }
}

/** The running event's progress along the panel's top edge. */
@Composable
private fun LiveProgressEdge(state: VideoOverlayUiState) {
    val colors = MaterialTheme.colorScheme
    val max = state.progressMax.coerceAtLeast(1)
    LinearProgressIndicator(
        progress = { state.progress.coerceIn(0, max).toFloat() / max },
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .testTag(VIDEO_OVERLAY_PROGRESS_TAG),
        color = colors.primary,
        trackColor = colors.onSurface.copy(alpha = 0.12f),
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
private fun SeekBar(state: VideoOverlayUiState, onSeekChange: (Int) -> Unit) {
    val hasTimes = state.nowStart.isNotEmpty() || state.remaining.isNotEmpty()
    if (state.progressMax <= 0 && !hasTimes) {
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.progressMax > 0) {
            SeekSlider(state, onSeekChange)
        }
        if (hasTimes) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SecondaryLine(state.nowStart, 13.sp)
                SecondaryLine(state.remaining, 13.sp)
            }
        }
    }
}

@Composable
private fun SeekSlider(state: VideoOverlayUiState, onSeekChange: (Int) -> Unit) {
    val seekLabel = stringResource(R.string.seek)
    val colors = MaterialTheme.colorScheme
    val max = state.progressMax.coerceAtLeast(1).toFloat()
    val value = state.progress.coerceIn(0, state.progressMax).toFloat()
    // Playback keeps moving state.progress, and keeps reporting the old position
    // until a seek lands: the thumb follows the drag, then the seek target.
    var dragValue by remember(state.progressMax) { mutableStateOf<Float?>(null) }
    var seekTarget by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(seekTarget) {
        val target = seekTarget ?: return@LaunchedEffect
        // VLC lands on keyframes, not on the exact target.
        val tolerance = maxOf(2f, max * 0.01f)
        withTimeoutOrNull(SEEK_SETTLE_TIMEOUT_MS) {
            snapshotFlow { state.progress }.first { abs(it - target) <= tolerance }
        }
        seekTarget = null
    }
    Slider(
        value = dragValue ?: seekTarget ?: value,
        onValueChange = { newValue -> dragValue = newValue },
        onValueChangeFinished = {
            val target = dragValue
            dragValue = null
            if (target != null && state.seekable) {
                seekTarget = target
                onSeekChange(target.toInt())
            }
        },
        valueRange = 0f..max,
        enabled = state.progressEnabled && state.seekable,
        colors = SliderDefaults.colors(
            thumbColor = colors.primary,
            activeTrackColor = colors.primary,
            inactiveTrackColor = colors.onSurface.copy(alpha = 0.16f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = seekLabel }
    )
}

@Composable
private fun Transport(
    state: VideoOverlayUiState,
    callbacks: InfobarCallbacks,
    playFocus: FocusRequester?
) {
    if (state.showPvrControls) {
        RepeatOverlayButton(
            iconRes = R.drawable.ic_fast_rewind_dark,
            label = stringResource(R.string.rewind),
            onClick = callbacks.onRewind
        )
    }
    PlayPauseButton(
        playing = state.isPlaying,
        onClick = callbacks.onPlay,
        size = TransportPlaySize,
        iconSize = 32.dp,
        modifier = playFocus?.let { Modifier.focusRequester(it) } ?: Modifier
    )
    if (state.showPvrControls) {
        RepeatOverlayButton(
            iconRes = R.drawable.ic_fast_forward_dark,
            label = stringResource(R.string.forward),
            onClick = callbacks.onForward
        )
    }
}

/** Live: play/pause first. Hidden actions stay out; sleep timer is always there. */
@Composable
private fun ActionButtons(
    state: VideoOverlayUiState,
    callbacks: InfobarCallbacks,
    playFocus: FocusRequester?,
    withPlay: Boolean
) {
    if (withPlay) {
        PlayPauseButton(
            playing = state.isPlaying,
            onClick = callbacks.onPlay,
            modifier = playFocus?.let { Modifier.focusRequester(it) } ?: Modifier
        )
    }
    if (state.showAudioButton) {
        OverlayButton(
            iconRes = R.drawable.ic_action_audio_track,
            label = stringResource(R.string.audio_tracks),
            onClick = callbacks.onAudio
        )
    }
    if (state.showSubtitleButton) {
        OverlayButton(
            iconRes = R.drawable.ic_action_subtitle,
            label = stringResource(R.string.subtitles),
            onClick = callbacks.onSubtitle
        )
    }
    if (state.showInfoButton) {
        OverlayButton(
            iconRes = R.drawable.ic_menu_info_dark,
            label = stringResource(R.string.info),
            onClick = callbacks.onInfo
        )
    }
    if (state.showListButton) {
        OverlayButton(
            iconRes = R.drawable.ic_menu_list_dark,
            label = stringResource(R.string.services),
            onClick = callbacks.onList
        )
    }
    SleepTimerButton(state.sleepTimer, callbacks.onSleepTimer)
}

@Composable
private fun PlayPauseButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ButtonSize,
    iconSize: Dp = 24.dp
) {
    OverlayButton(
        iconRes = if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow,
        label = stringResource(if (playing) R.string.pause else R.string.play),
        onClick = onClick,
        modifier = modifier,
        size = size,
        iconSize = iconSize,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    )
}

/** Opens the sleep timer choice; a running timer shows its minutes left as a badge. */
@Composable
private fun SleepTimerButton(timer: SleepTimer, onClick: () -> Unit) {
    val running = timer as? SleepTimer.Running
    val label = if (running != null) {
        stringResource(R.string.sleep_timer_left, running.minutesLeft)
    } else {
        stringResource(R.string.sleeptimer)
    }
    BadgedBox(
        badge = {
            if (running != null) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // The button's label already says the minutes left.
                    modifier = Modifier
                        .testTag(VIDEO_OVERLAY_SLEEP_BADGE_TAG)
                        .clearAndSetSemantics {}
                ) {
                    Text(running.minutesLeft.toString())
                }
            }
        }
    ) {
        OverlayButton(
            iconRes = R.drawable.ic_action_sleep_timer,
            label = label,
            onClick = onClick
        )
    }
}

/** [OverlayButton] with [rememberRepeatingClick]. */
@Composable
private fun RepeatOverlayButton(iconRes: Int, label: String, onClick: () -> Unit) {
    val (interactionSource, click) = rememberRepeatingClick(onClick)
    OverlayButton(
        iconRes = iconRes,
        label = label,
        onClick = click,
        interactionSource = interactionSource
    )
}

@Composable
private fun OverlayButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ButtonSize,
    iconSize: Dp = ButtonIconSize,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    interactionSource: MutableInteractionSource? = null
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        colors = colors,
        interactionSource = interactionSource
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun LivePicon(state: VideoOverlayUiState, width: Dp, height: Dp, corner: Dp) {
    PiconImage(
        reference = state.zapCurrentRef,
        name = state.title,
        contentDescription = null,
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(corner))
    )
}

@Composable
private fun PanelTitle(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun TitleWithTimes(title: String, titleSize: TextUnit, times: String, timesSize: TextUnit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Unweighted children measure first: the title keeps its width, the times truncate.
        PanelTitle(title, titleSize)
        SecondaryLine(times, timesSize, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun SecondaryLine(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    if (text.isEmpty()) {
        return
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun NowTitle(text: String, fontSize: TextUnit) {
    if (text.isEmpty()) {
        return
    }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** "21:45  Next title", the start time in semibold. */
@Composable
private fun NextLine(state: VideoOverlayUiState, fontSize: TextUnit) {
    if (!state.hasNext) {
        return
    }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(state.nextStart) }
            append("  ")
            append(state.nextTitle)
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun InfobarClock(nowMs: Long, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    Text(
        text = format.format(Date(nowMs)),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier.testTag(VIDEO_OVERLAY_CLOCK_TAG)
    )
}

/**
 * The overlay's programme or recording details and its track / sleep timer choice. [tv]
 * shows the details as a dialog: a sheet's drag handle means nothing on a remote.
 */
@Composable
fun VideoOverlayModals(state: VideoOverlayUiState, tv: Boolean) {
    val epg = state.epgDetailContent
    val movie = state.movieDetailContent
    if (tv) {
        if (epg != null) {
            TvPlayerEpgDetailDialog(epg, onDismiss = { state.epgDetailContent = null })
        }
        if (movie != null) {
            TvPlayerMovieDetailDialog(movie, onDismiss = { state.movieDetailContent = null })
        }
    } else {
        if (epg != null) {
            EpgDetailModalSheet(
                content = epg,
                onDismiss = { state.epgDetailContent = null },
                onSetTimer = { state.epgDetailContent = null },
                onEditTimer = { state.epgDetailContent = null },
                onImdb = { state.epgDetailContent = null },
                onSimilar = { state.epgDetailContent = null },
                showActions = false
            )
        }
        if (movie != null) {
            MovieDetailModalSheet(
                content = movie,
                onDismiss = { state.movieDetailContent = null }
            )
        }
    }
    val choiceTitle = state.choiceTitle
    if (choiceTitle != null) {
        SimpleChoiceAlertDialog(
            title = choiceTitle,
            items = state.choiceItems,
            onDismiss = { state.dismissChoice() },
            onChoice = { index ->
                val tag = state.choiceDialogTag
                val ids = state.choiceActionIds
                if (tag != null && index in ids.indices) {
                    state.onChoiceAction?.invoke(ids[index], tag)
                }
                state.dismissChoice()
            }
        )
    }
}

/**
 * Hold-to-repeat for a button: fires [onClick] on press-down, then every [repeatDelayMs] after
 * [initialDelayMs] while held. Pass the returned source to the button and use the returned
 * click, which drops the release click so a hold does not step once more.
 */
@Composable
internal fun rememberRepeatingClick(
    onClick: () -> Unit,
    initialDelayMs: Long = 500L,
    repeatDelayMs: Long = 300L
): Pair<MutableInteractionSource, () -> Unit> {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var suppressReleaseClick by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed) {
            return@LaunchedEffect
        }
        suppressReleaseClick = true
        onClick()
        delay(initialDelayMs)
        while (true) {
            onClick()
            delay(repeatDelayMs)
        }
    }
    return interactionSource to {
        if (suppressReleaseClick) {
            suppressReleaseClick = false
        } else {
            onClick()
        }
    }
}

fun ComposeView.bindVideoOverlayScreen(
    state: VideoOverlayUiState,
    onPlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onInfo: () -> Unit,
    onList: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onSleepTimer: () -> Unit,
    onSeekChange: (Int) -> Unit
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    // Focusable shell so nextFocusDown from the zap row lands here; then forward into Compose.
    isFocusable = true
    isFocusableInTouchMode = true
    val firstControlFocus = FocusRequester()
    setContent {
        DreamDroidTheme(forceDark = true) {
            VideoOverlayScreen(
                state = state,
                onPlay = onPlay,
                onRewind = onRewind,
                onForward = onForward,
                onInfo = onInfo,
                onList = onList,
                onAudio = onAudio,
                onSubtitle = onSubtitle,
                onSleepTimer = onSleepTimer,
                onSeekChange = onSeekChange,
                firstControlFocusRequester = firstControlFocus,
                modifier = Modifier.focusGroup()
            )
            VideoOverlayModals(state, tv = false)
        }
    }
    setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            post {
                firstControlFocus.requestFocus()
            }
        }
    }
}

fun ComposeView.bindVideoOverlayTopBar(
    onBack: () -> Unit,
    onPictureInPicture: (() -> Unit)?,
    channelListOpen: State<Boolean>
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme(forceDark = true) {
            VideoOverlayTopBar(
                onBack = onBack,
                onPictureInPicture = onPictureInPicture,
                channelListOpen = channelListOpen.value
            )
        }
    }
}
