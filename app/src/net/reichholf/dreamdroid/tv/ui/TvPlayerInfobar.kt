package net.reichholf.dreamdroid.tv.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.material3.Text as PhoneText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.util.Date
import kotlinx.coroutines.delay
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.PiconImage
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvPillShape
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTopBarItemScale
import net.reichholf.dreamdroid.ui.video.SleepTimer
import net.reichholf.dreamdroid.ui.video.VideoOverlayModals
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState
import net.reichholf.dreamdroid.ui.video.rememberRepeatingClick

const val TV_PLAYER_INFOBAR_TAG = "tv_player_infobar"

private val InfobarShape = RoundedCornerShape(16.dp)
private const val INFOBAR_ALPHA = 0.92f
private val InfobarProgressHeight = 6.dp
private val InfobarButtonSize = 48.dp
private val InfobarIconSize = 24.dp
private val PiconWidth = 96.dp
private val PiconHeight = 58.dp
private const val MINUTE_MS = 60_000L

private enum class InfobarAction { Audio, Subtitle, Info, List, Sleep }

/**
 * The TV player's compact infobar: one panel with the channel (or recording), the running and
 * next event, a clock and round actions; recordings add transport controls and their position.
 */
@Composable
fun TvPlayerInfobar(
    state: VideoOverlayUiState,
    onPlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onInfo: () -> Unit,
    onList: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
    firstControlFocusRequester: FocusRequester? = null
) {
    val colors = PhoneMaterialTheme.colorScheme
    val recording = state.showPvrControls
    val firstAction = when {
        recording || firstControlFocusRequester == null -> null
        state.showAudioButton -> InfobarAction.Audio
        state.showSubtitleButton -> InfobarAction.Subtitle
        state.showInfoButton -> InfobarAction.Info
        state.showListButton -> InfobarAction.List
        else -> InfobarAction.Sleep
    }
    val actions: @Composable () -> Unit = {
        InfobarActions(
            state = state,
            firstAction = firstAction,
            firstControlFocusRequester = firstControlFocusRequester,
            onInfo = onInfo,
            onList = onList,
            onAudio = onAudio,
            onSubtitle = onSubtitle,
            onSleepTimer = onSleepTimer
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(InfobarShape)
            .background(colors.surfaceContainerLow.copy(alpha = INFOBAR_ALPHA))
            .testTag(TV_PLAYER_INFOBAR_TAG)
    ) {
        if (!recording && state.progressMax > 0) {
            InfobarProgress(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tv_player_infobar_progress")
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!recording) {
                    PiconImage(
                        reference = state.zapCurrentRef,
                        name = state.title,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(width = PiconWidth, height = PiconHeight)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                InfobarText(state = state, modifier = Modifier.weight(1f))
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfobarClock()
                    actions()
                }
            }
            if (recording) {
                InfobarTransport(
                    state = state,
                    onPlay = onPlay,
                    onRewind = onRewind,
                    onForward = onForward,
                    playFocusRequester = firstControlFocusRequester
                )
            }
        }
    }
}

@Composable
private fun InfobarText(state: VideoOverlayUiState, modifier: Modifier = Modifier) {
    val colors = PhoneMaterialTheme.colorScheme
    val context = LocalContext.current
    val meta = if (state.showPvrControls) {
        null
    } else {
        nowEventTimes(context, state.nowEvent) ?: state.nowStart.ifEmpty { null }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.title,
                color = colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (meta != null) {
                Text(
                    text = meta,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        if (state.nowTitle.isNotEmpty()) {
            Text(
                text = state.nowTitle,
                color = if (state.showPvrControls) colors.onSurfaceVariant else colors.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!state.showPvrControls && state.hasNext) {
            Row {
                Text(
                    text = state.nextStart,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = state.nextTitle,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun InfobarClock() {
    val context = LocalContext.current
    val format = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE_MS - now % MINUTE_MS)
            now = System.currentTimeMillis()
        }
    }
    Text(
        text = format.format(Date(now)),
        color = PhoneMaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("tv_player_infobar_clock")
    )
}

@Composable
private fun InfobarProgress(state: VideoOverlayUiState, modifier: Modifier = Modifier) {
    val colors = PhoneMaterialTheme.colorScheme
    val max = state.progressMax.coerceAtLeast(1)
    LinearProgressIndicator(
        progress = { state.progress.coerceIn(0, max).toFloat() / max },
        modifier = modifier.height(InfobarProgressHeight),
        color = colors.primary,
        trackColor = colors.onSurface.copy(alpha = 0.12f),
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
private fun InfobarTransport(
    state: VideoOverlayUiState,
    onPlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    playFocusRequester: FocusRequester?
) {
    val colors = PhoneMaterialTheme.colorScheme
    val (rewindSource, rewindClick) = rememberRepeatingClick(onRewind)
    val (forwardSource, forwardClick) = rememberRepeatingClick(onForward)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InfobarButton(
            label = stringResource(R.string.rewind),
            painter = painterResource(R.drawable.ic_fast_rewind_dark),
            onClick = rewindClick,
            expandOnFocus = false,
            interactionSource = rewindSource
        )
        InfobarButton(
            label = stringResource(R.string.play),
            painter = painterResource(R.drawable.ic_play_circle_outline_dark),
            onClick = onPlay,
            expandOnFocus = false,
            modifier = playFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
        )
        InfobarButton(
            label = stringResource(R.string.forward),
            painter = painterResource(R.drawable.ic_fast_forward_dark),
            onClick = forwardClick,
            expandOnFocus = false,
            interactionSource = forwardSource
        )
        Text(
            text = state.nowStart,
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp)
        )
        InfobarProgress(
            state = state,
            modifier = Modifier
                .weight(1f)
                .clip(DreamDroidTvPillShape)
                .testTag("tv_player_infobar_progress")
        )
        Text(
            text = state.nowDuration,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun InfobarActions(
    state: VideoOverlayUiState,
    firstAction: InfobarAction?,
    firstControlFocusRequester: FocusRequester?,
    onInfo: () -> Unit,
    onList: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onSleepTimer: () -> Unit
) {
    fun focusFor(action: InfobarAction): Modifier =
        if (action == firstAction && firstControlFocusRequester != null) {
            Modifier.focusRequester(firstControlFocusRequester)
        } else {
            Modifier
        }
    Row(
        modifier = Modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.showAudioButton) {
            InfobarButton(
                label = stringResource(R.string.audio_tracks),
                painter = painterResource(R.drawable.ic_action_audio_track),
                onClick = onAudio,
                modifier = focusFor(InfobarAction.Audio)
            )
        }
        if (state.showSubtitleButton) {
            InfobarButton(
                label = stringResource(R.string.subtitles),
                painter = painterResource(R.drawable.ic_action_subtitle),
                onClick = onSubtitle,
                modifier = focusFor(InfobarAction.Subtitle)
            )
        }
        if (state.showInfoButton) {
            InfobarButton(
                label = stringResource(R.string.info),
                painter = painterResource(R.drawable.ic_menu_info_dark),
                onClick = onInfo,
                modifier = focusFor(InfobarAction.Info)
            )
        }
        if (state.showListButton) {
            InfobarButton(
                label = stringResource(R.string.services),
                painter = painterResource(R.drawable.ic_menu_list_dark),
                onClick = onList,
                modifier = focusFor(InfobarAction.List)
            )
        }
        val running = state.sleepTimer as? SleepTimer.Running
        BadgedBox(
            badge = {
                if (running != null) {
                    Badge(modifier = Modifier.testTag("tv_player_infobar_sleep_badge")) {
                        PhoneText(running.minutesLeft.toString())
                    }
                }
            }
        ) {
            InfobarButton(
                label = if (running != null) {
                    stringResource(R.string.sleep_timer_left, running.minutesLeft)
                } else {
                    stringResource(R.string.sleeptimer)
                },
                painter = painterResource(R.drawable.ic_action_sleep_timer),
                onClick = onSleepTimer,
                modifier = focusFor(InfobarAction.Sleep)
            )
        }
    }
}

/**
 * Round 48dp control in the hub's top bar colours. With [expandOnFocus] it widens on focus to
 * show [label] beside the icon, so focus reads from across the room.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InfobarButton(
    label: String,
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandOnFocus: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val colors = PhoneMaterialTheme.colorScheme
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = InfobarButtonSize)
            .widthIn(min = InfobarButtonSize)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
            },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            focusedContainerColor = colors.inverseSurface,
            focusedContentColor = colors.inverseOnSurface,
            pressedContainerColor = colors.inverseSurface,
            pressedContentColor = colors.inverseOnSurface
        ),
        scale = DreamDroidTvTopBarItemScale,
        shape = ClickableSurfaceDefaults.shape(shape = DreamDroidTvPillShape),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .height(InfobarButtonSize)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(InfobarIconSize)
            )
            AnimatedVisibility(visible = expandOnFocus && focused) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                )
            }
        }
    }
}

fun ComposeView.bindTvPlayerInfobar(
    state: VideoOverlayUiState,
    onPlay: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onInfo: () -> Unit,
    onList: () -> Unit,
    onAudio: () -> Unit,
    onSubtitle: () -> Unit,
    onSleepTimer: () -> Unit
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    // Focusable shell so the View focus search lands here; then forward into Compose.
    isFocusable = true
    isFocusableInTouchMode = true
    val firstControlFocus = FocusRequester()
    setContent {
        DreamDroidTvTheme(fillBackground = false, forceDark = true) {
            TvPlayerInfobar(
                state = state,
                onPlay = onPlay,
                onRewind = onRewind,
                onForward = onForward,
                onInfo = onInfo,
                onList = onList,
                onAudio = onAudio,
                onSubtitle = onSubtitle,
                onSleepTimer = onSleepTimer,
                firstControlFocusRequester = firstControlFocus,
                modifier = Modifier.focusGroup()
            )
            VideoOverlayModals(state, uncappedDetailSheets = true)
        }
    }
    setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            post {
                try {
                    firstControlFocus.requestFocus()
                } catch (_: IllegalStateException) {
                    // Composition not ready yet.
                }
            }
        }
    }
}
