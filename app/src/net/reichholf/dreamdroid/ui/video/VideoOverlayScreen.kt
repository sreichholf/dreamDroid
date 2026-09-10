package net.reichholf.dreamdroid.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

/**
 * Mutable overlay chrome state driven by [net.reichholf.dreamdroid.fragment.VideoOverlayFragment].
 */
class VideoOverlayUiState {
    var title by mutableStateOf("")
    var nowStart by mutableStateOf("")
    var nowTitle by mutableStateOf("")
    var nowDuration by mutableStateOf("")
    var showNow by mutableStateOf(false)
    var nextStart by mutableStateOf("")
    var nextTitle by mutableStateOf("")
    var nextDuration by mutableStateOf("")
    var hasNext by mutableStateOf(false)
    var showPvrControls by mutableStateOf(false)
    var progressMax by mutableIntStateOf(0)
    var progress by mutableIntStateOf(0)
    var progressEnabled by mutableStateOf(false)
    var seekable by mutableStateOf(false)
    var showAudioButton by mutableStateOf(false)
    var showSubtitleButton by mutableStateOf(false)
    var showListButton by mutableStateOf(false)
    var showInfoButton by mutableStateOf(false)
}

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
    onSeekChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    firstControlFocusRequester: FocusRequester? = null,
) {
    val playLabel = stringResource(R.string.play)
    val rewindLabel = stringResource(R.string.rewind)
    val forwardLabel = stringResource(R.string.forward)
    val infoLabel = stringResource(R.string.info)
    val listLabel = stringResource(R.string.services)
    val audioLabel = stringResource(R.string.audio_tracks)
    val subtitleLabel = stringResource(R.string.subtitles)
    val seekLabel = stringResource(R.string.seek)
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleLarge,
            color = onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics { contentDescription = "overlay_title" },
        )

        if (state.showNow) {
            EventRow(
                start = state.nowStart,
                title = state.nowTitle,
                duration = state.nowDuration,
                rowDescription = stringResource(R.string.now),
            )
        }

        if (state.hasNext) {
            EventRow(
                start = state.nextStart,
                title = state.nextTitle,
                duration = state.nextDuration,
                rowDescription = stringResource(R.string.next),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.showPvrControls) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RepeatIconButton(
                        onClick = onRewind,
                        painter = painterResource(R.drawable.ic_fast_rewind_dark),
                        contentDescription = rewindLabel,
                        modifier = firstControlFocusRequester?.let { Modifier.focusRequester(it) }
                            ?: Modifier,
                    )
                    IconButton(onClick = onPlay) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_circle_outline_dark),
                            contentDescription = playLabel,
                            tint = onSurface,
                        )
                    }
                    RepeatIconButton(
                        onClick = onForward,
                        painter = painterResource(R.drawable.ic_fast_forward_dark),
                        contentDescription = forwardLabel,
                    )
                }
            }

            if (state.progressMax > 0) {
                val max = state.progressMax.coerceAtLeast(1).toFloat()
                val value = state.progress.coerceIn(0, state.progressMax).toFloat()
                Slider(
                    value = value,
                    onValueChange = { newValue ->
                        if (state.seekable) {
                            onSeekChange(newValue.toInt())
                        }
                    },
                    valueRange = 0f..max,
                    enabled = state.progressEnabled && state.seekable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = seekLabel },
                )
            }
        }

        val firstActionKey = when {
            state.showPvrControls || firstControlFocusRequester == null -> null
            state.showAudioButton -> "audio"
            state.showInfoButton -> "info"
            state.showListButton -> "list"
            state.showSubtitleButton -> "subtitle"
            else -> null
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .focusGroup(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.showAudioButton) {
                IconButton(
                    onClick = onAudio,
                    modifier = if (firstActionKey == "audio") {
                        Modifier.focusRequester(firstControlFocusRequester!!)
                    } else {
                        Modifier
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_audio_track),
                        contentDescription = audioLabel,
                        tint = onSurface,
                    )
                }
            }
            if (state.showInfoButton) {
                IconButton(
                    onClick = onInfo,
                    modifier = if (firstActionKey == "info") {
                        Modifier.focusRequester(firstControlFocusRequester!!)
                    } else {
                        Modifier
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu_info_dark),
                        contentDescription = infoLabel,
                        tint = onSurface,
                    )
                }
            }
            if (state.showListButton) {
                IconButton(
                    onClick = onList,
                    modifier = if (firstActionKey == "list") {
                        Modifier.focusRequester(firstControlFocusRequester!!)
                    } else {
                        Modifier
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu_list_dark),
                        contentDescription = listLabel,
                        tint = onSurface,
                    )
                }
            }
            if (state.showSubtitleButton) {
                IconButton(
                    onClick = onSubtitle,
                    modifier = if (firstActionKey == "subtitle") {
                        Modifier.focusRequester(firstControlFocusRequester!!)
                    } else {
                        Modifier
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_subtitle),
                        contentDescription = subtitleLabel,
                        tint = onSurface,
                    )
                }
            }
        }
    }
}

/**
 * IconButton that fires [onClick] on press-down, then repeats while held
 * (parity with [net.reichholf.dreamdroid.view.OnRepeatListener]: 500ms then every 300ms).
 * Suppresses the IconButton release click so a hold does not seek one extra step.
 */
@Composable
private fun RepeatIconButton(
    onClick: () -> Unit,
    painter: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
    initialDelayMs: Long = 500L,
    repeatDelayMs: Long = 300L,
) {
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
    IconButton(
        onClick = {
            if (suppressReleaseClick) {
                suppressReleaseClick = false
                return@IconButton
            }
            onClick()
        },
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EventRow(
    start: String,
    title: String,
    duration: String,
    rowDescription: String,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = rowDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = start,
            style = MaterialTheme.typography.bodyMedium,
            color = onSurface,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = duration,
            style = MaterialTheme.typography.bodyMedium,
            color = onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
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
    onSeekChange: (Int) -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    // Focusable shell so nextFocusDown from servicelist lands here; we then forward into Compose.
    isFocusable = true
    isFocusableInTouchMode = true
    val firstControlFocus = FocusRequester()
    setContent {
        DreamDroidTheme {
            VideoOverlayScreen(
                state = state,
                onPlay = onPlay,
                onRewind = onRewind,
                onForward = onForward,
                onInfo = onInfo,
                onList = onList,
                onAudio = onAudio,
                onSubtitle = onSubtitle,
                onSeekChange = onSeekChange,
                firstControlFocusRequester = firstControlFocus,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
            )
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
