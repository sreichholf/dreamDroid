package net.reichholf.dreamdroid.ui.remote

import android.content.res.ColorStateList
import android.graphics.Rect
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.ImageViewCompat
import kotlin.math.abs
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.Remote

enum class VirtualRemoteLayout {
    Full,
    Simple,
    QuickZap
}

private val KeyRed = Color(0xFFC62828)
private val KeyGreen = Color(0xFF2E7D32)
private val KeyYellow = Color(0xFFF9A825)
private val KeyBlue = Color(0xFF1565C0)
private val KeyOnYellow = Color(0xFF212121)

private data class RemoteMetrics(
    val keyWidth: Dp,
    val keyHeight: Dp,
    val keyHeightLow: Dp,
    val gap: Dp,
    val navKeySize: Dp,
    val sectionExtra: Dp,
    val verticalPadding: Dp,
    val labelSp: Float,
    val iconDp: Dp
)

private val LocalRemoteMetrics = compositionLocalOf {
    RemoteMetrics(
        keyWidth = 56.dp,
        keyHeight = 40.dp,
        keyHeightLow = 30.dp,
        gap = 4.dp,
        navKeySize = 56.dp,
        sectionExtra = 2.dp,
        verticalPadding = 24.dp,
        labelSp = 12f,
        iconDp = 24.dp
    )
}

internal const val VIRTUAL_REMOTE_LAYOUT_TOGGLE_TAG = "virtual_remote_layout_toggle"

@Composable
fun VirtualRemoteScreen(
    layout: VirtualRemoteLayout,
    playButtonAsPlayPause: Boolean,
    onKey: (keyCode: Int, longClick: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onToggleLayout: (() -> Unit)? = null,
    toggleIconRes: Int = R.drawable.ic_action_list,
    toggleContentDescription: String? = null
) {
    val view = LocalView.current
    val density = LocalDensity.current
    var overflowBottom by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val visible = Rect()
                if (!view.getGlobalVisibleRect(visible)) {
                    return@onGloballyPositioned
                }
                val clippedPx = (coordinates.size.height - visible.height()).coerceAtLeast(0)
                val clippedDp = with(density) { clippedPx.toDp() }
                if (abs(clippedDp.value - overflowBottom.value) > 0.5f) {
                    overflowBottom = clippedDp
                }
            }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = overflowBottom)
                .clipToBounds()
        ) {
            val horizontalPad = 16.dp
            val fit = VirtualRemoteFit.metrics(
                availableWidthDp = (maxWidth - horizontalPad * 2).value,
                availableHeightDp = maxHeight.value,
                layout = layout
            )
            val metrics = RemoteMetrics(
                keyWidth = fit.keyWidth.dp,
                keyHeight = fit.keyHeight.dp,
                keyHeightLow = fit.keyHeightLow.dp,
                gap = fit.gap.dp,
                navKeySize = fit.navKeySize.dp,
                sectionExtra = fit.sectionExtra.dp,
                verticalPadding = fit.verticalPadding.dp,
                labelSp = (12f * (fit.keyWidth / VirtualRemoteFit.PREFERRED_KEY_WIDTH_DP))
                    .coerceIn(9f, 16f),
                iconDp = (24f * (fit.keyWidth / VirtualRemoteFit.PREFERRED_KEY_WIDTH_DP))
                    .coerceIn(16f, 32f)
                    .dp
            )
            val padMaxWidth = metrics.keyWidth * 5 + metrics.gap * 4
            val scroll = rememberScrollState()
            val padModifier = Modifier
                .widthIn(max = padMaxWidth)
                .then(
                    if (fit.fitsWithoutScroll) {
                        Modifier
                    } else {
                        Modifier.verticalScroll(scroll)
                    }
                )
                .padding(vertical = metrics.verticalPadding / 2)

            CompositionLocalProvider(LocalRemoteMetrics provides metrics) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (fit.fitsWithoutScroll) {
                        Alignment.Center
                    } else {
                        Alignment.TopCenter
                    }
                ) {
                    Column(
                        modifier = padModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            metrics.gap * 2 + metrics.sectionExtra
                        )
                    ) {
                        when (layout) {
                            VirtualRemoteLayout.QuickZap -> QuickZapPad(onKey = onKey)

                            VirtualRemoteLayout.Simple -> SimplePad(onKey = onKey)

                            VirtualRemoteLayout.Full -> FullPad(
                                playButtonAsPlayPause = playButtonAsPlayPause,
                                onKey = onKey
                            )
                        }
                    }
                }
            }

            if (onToggleLayout != null && toggleContentDescription != null) {
                SmallFloatingActionButton(
                    onClick = onToggleLayout,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag(VIRTUAL_REMOTE_LAYOUT_TOGGLE_TAG)
                ) {
                    Icon(
                        painter = painterResource(toggleIconRes),
                        contentDescription = toggleContentDescription
                    )
                }
            }
        }
    }
}

@Composable
private fun FullPad(playButtonAsPlayPause: Boolean, onKey: (Int, Boolean) -> Unit) {
    NumberVolumeBouquetPad(onKey = onKey)
    ColorKeysRow(onKey = onKey)
    NavigationPad(onKey = onKey)
    MuteExitRow(onKey = onKey)
    TransportPad(playButtonAsPlayPause = playButtonAsPlayPause, onKey = onKey)
    SourcePad(onKey = onKey)
}

@Composable
private fun SimplePad(onKey: (Int, Boolean) -> Unit) {
    NumberVolumeBouquetPad(onKey = onKey)
    NavigationPad(onKey = onKey)
    MuteExitRow(onKey = onKey)
    ColorKeysRow(onKey = onKey)
    SourcePad(onKey = onKey)
}

@Composable
private fun QuickZapPad(onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        RemoteKey(label = "Help", keyCode = Remote.KEY_HELP, onKey = onKey, height = m.keyHeightLow)
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(label = "V+", keyCode = Remote.KEY_VOLP, onKey = onKey, raised = true)
            RemoteKey(label = "V-", keyCode = Remote.KEY_VOLM, onKey = onKey, raised = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(label = "Mute", keyCode = Remote.KEY_MUTE, onKey = onKey)
            RemoteKey(
                label = "Exit",
                keyCode = Remote.KEY_EXIT,
                onKey = onKey,
                container = KeyRed,
                contentColor = Color.White
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(label = "B+", keyCode = Remote.KEY_BOUP, onKey = onKey, raised = true)
            RemoteKey(label = "B-", keyCode = Remote.KEY_BOUM, onKey = onKey, raised = true)
        }
        RemoteKey(
            label = "PWR",
            keyCode = Remote.KEY_POWER,
            onKey = onKey,
            container = KeyRed,
            contentColor = Color.White,
            height = m.keyHeightLow
        )
    }
    NavigationPad(onKey = onKey)
    ColorKeysRow(onKey = onKey, height = m.keyHeightLow)
}

@Composable
private fun NumberVolumeBouquetPad(onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(
                label = "Help",
                keyCode = Remote.KEY_HELP,
                onKey = onKey,
                height = m.keyHeightLow
            )
            RemoteKey(label = "V+", keyCode = Remote.KEY_VOLP, onKey = onKey, raised = true)
            RemoteKey(label = "V-", keyCode = Remote.KEY_VOLM, onKey = onKey, raised = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
                DigitKey("1", Remote.KEY_1, onKey)
                DigitKey("2", Remote.KEY_2, onKey)
                DigitKey("3", Remote.KEY_3, onKey)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
                DigitKey("4", Remote.KEY_4, onKey)
                DigitKey("5", Remote.KEY_5, onKey)
                DigitKey("6", Remote.KEY_6, onKey)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
                DigitKey("7", Remote.KEY_7, onKey)
                DigitKey("8", Remote.KEY_8, onKey)
                DigitKey("9", Remote.KEY_9, onKey)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
                DigitKey("<", Remote.KEY_PREV, onKey)
                DigitKey("0", Remote.KEY_0, onKey)
                DigitKey(">", Remote.KEY_NEXT, onKey)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(
                label = "PWR",
                keyCode = Remote.KEY_POWER,
                onKey = onKey,
                container = KeyRed,
                contentColor = Color.White,
                height = m.keyHeightLow
            )
            RemoteKey(label = "B+", keyCode = Remote.KEY_BOUP, onKey = onKey, raised = true)
            RemoteKey(label = "B-", keyCode = Remote.KEY_BOUM, onKey = onKey, raised = true)
        }
    }
}

@Composable
private fun ColorKeysRow(onKey: (Int, Boolean) -> Unit, height: Dp? = null) {
    val m = LocalRemoteMetrics.current
    val rowHeight = height ?: m.keyHeightLow
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        RemoteKey(
            label = "R",
            description = "Red",
            keyCode = Remote.KEY_RED,
            onKey = onKey,
            container = KeyRed,
            contentColor = Color.White,
            height = rowHeight
        )
        RemoteKey(
            label = "G",
            description = "Green",
            keyCode = Remote.KEY_GREEN,
            onKey = onKey,
            container = KeyGreen,
            contentColor = Color.White,
            height = rowHeight
        )
        RemoteKey(
            label = "Y",
            description = "Yellow",
            keyCode = Remote.KEY_YELLOW,
            onKey = onKey,
            container = KeyYellow,
            contentColor = KeyOnYellow,
            height = rowHeight
        )
        RemoteKey(
            label = "B",
            description = "Blue",
            keyCode = Remote.KEY_BLUE,
            onKey = onKey,
            container = KeyBlue,
            contentColor = Color.White,
            height = rowHeight
        )
    }
}

@Composable
private fun NavigationPad(onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    val size = m.navKeySize
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(m.gap)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(
                label = stringResource(R.string.info),
                keyCode = Remote.KEY_INFO,
                onKey = onKey,
                width = size,
                height = size
            )
            IconRemoteKey(
                description = "Up",
                keyCode = Remote.KEY_UP,
                iconRes = R.drawable.ic_key_up,
                onKey = onKey,
                width = size,
                height = size,
                raised = true
            )
            RemoteKey(
                label = "Menu",
                keyCode = Remote.KEY_MENU,
                onKey = onKey,
                width = size,
                height = size
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            IconRemoteKey(
                description = "Left",
                keyCode = Remote.KEY_LEFT,
                iconRes = R.drawable.ic_key_left,
                onKey = onKey,
                width = size,
                height = size,
                raised = true
            )
            RemoteKey(
                label = stringResource(R.string.ok),
                keyCode = Remote.KEY_OK,
                onKey = onKey,
                width = size,
                height = size,
                raised = true
            )
            IconRemoteKey(
                description = "Right",
                keyCode = Remote.KEY_RIGHT,
                iconRes = R.drawable.ic_key_right,
                onKey = onKey,
                width = size,
                height = size,
                raised = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(
                label = "Audio",
                keyCode = Remote.KEY_AUDIO,
                onKey = onKey,
                width = size,
                height = size
            )
            IconRemoteKey(
                description = "Down",
                keyCode = Remote.KEY_DOWN,
                iconRes = R.drawable.ic_key_down,
                onKey = onKey,
                width = size,
                height = size,
                raised = true
            )
            RemoteKey(
                label = "PVR",
                keyCode = Remote.KEY_PVR,
                onKey = onKey,
                width = size,
                height = size
            )
        }
    }
}

@Composable
private fun MuteExitRow(onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    val width = m.keyWidth * 1.7f
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        RemoteKey(
            label = "Mute",
            keyCode = Remote.KEY_MUTE,
            onKey = onKey,
            width = width,
            height = m.keyHeightLow
        )
        RemoteKey(
            label = "Exit",
            keyCode = Remote.KEY_EXIT,
            onKey = onKey,
            width = width,
            height = m.keyHeightLow,
            container = KeyRed,
            contentColor = Color.White
        )
    }
}

@Composable
private fun TransportPad(playButtonAsPlayPause: Boolean, onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        IconRemoteKey(
            description = "Rewind",
            keyCode = Remote.KEY_REWIND,
            iconRes = R.drawable.ic_media_previous_dark,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight
        )
        if (playButtonAsPlayPause) {
            IconRemoteKey(
                description = "Play/Pause",
                keyCode = Remote.KEY_PLAYPAUSE,
                iconRes = R.drawable.ic_media_play_pause_dark,
                onKey = onKey,
                width = m.keyWidth,
                height = m.keyHeight
            )
        } else {
            IconRemoteKey(
                description = "Play",
                keyCode = Remote.KEY_PLAY,
                iconRes = R.drawable.ic_media_play_dark,
                onKey = onKey,
                width = m.keyWidth,
                height = m.keyHeight
            )
        }
        IconRemoteKey(
            description = "Stop",
            keyCode = Remote.KEY_STOP,
            iconRes = R.drawable.ic_media_stop_dark,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight
        )
        IconRemoteKey(
            description = "Forward",
            keyCode = Remote.KEY_FORWARD,
            iconRes = R.drawable.ic_media_next_dark,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight
        )
    }
}

@Composable
private fun SourcePad(onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        RemoteKey(
            label = "TV",
            keyCode = Remote.KEY_TV,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight
        )
        RemoteKey(
            label = "RADIO",
            keyCode = Remote.KEY_RADIO,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight
        )
        RemoteKey(
            label = "TEXT",
            keyCode = Remote.KEY_TEXT,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight
        )
        RemoteKey(
            label = "REC",
            keyCode = Remote.KEY_RECORD,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight,
            contentColor = KeyRed
        )
    }
}

@Composable
private fun DigitKey(label: String, keyCode: Int, onKey: (Int, Boolean) -> Unit) {
    RemoteKey(
        label = label,
        keyCode = keyCode,
        onKey = onKey,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteKey(
    label: String,
    keyCode: Int,
    onKey: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp? = null,
    container: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    raised: Boolean = false,
    showLabel: Boolean = true,
    fontWeight: FontWeight = FontWeight.Normal,
    description: String = label
) {
    val m = LocalRemoteMetrics.current
    val scheme = MaterialTheme.colorScheme
    val keyWidth = width ?: m.keyWidth
    val keyHeight = height ?: m.keyHeight
    val bg = when {
        container != Color.Unspecified -> container
        raised -> scheme.secondaryContainer
        else -> scheme.surfaceContainerHighest
    }
    val fg = when {
        contentColor != Color.Unspecified -> contentColor
        raised -> scheme.onSecondaryContainer
        else -> scheme.onSurface
    }
    Box(
        modifier = modifier
            .width(keyWidth)
            .height(keyHeight)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .semantics { contentDescription = description }
            .combinedClickable(
                onClick = { onKey(keyCode, false) },
                onLongClick = { onKey(keyCode, true) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showLabel) {
            Text(
                text = label,
                color = fg,
                fontSize = m.labelSp.sp,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconRemoteKey(
    description: String,
    keyCode: Int,
    iconRes: Int,
    onKey: (Int, Boolean) -> Unit,
    width: Dp,
    height: Dp,
    container: Color = Color.Unspecified,
    raised: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        container != Color.Unspecified -> container
        raised -> scheme.secondaryContainer
        else -> scheme.surfaceContainerHighest
    }
    val fg = if (raised) scheme.onSecondaryContainer else scheme.onSurface
    val fgArgb = fg.toArgb()
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .semantics { contentDescription = description }
            .combinedClickable(
                onClick = { onKey(keyCode, false) },
                onLongClick = { onKey(keyCode, true) }
            ),
        contentAlignment = Alignment.Center
    ) {
        // layer-list / rotate drawables are not VectorDrawables; painterResource cannot load them.
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageResource(iconRes)
                    ImageViewCompat.setImageTintList(
                        this,
                        ColorStateList.valueOf(fgArgb)
                    )
                }
            },
            update = { imageView ->
                imageView.setImageResource(iconRes)
                ImageViewCompat.setImageTintList(
                    imageView,
                    ColorStateList.valueOf(fgArgb)
                )
            },
            modifier = Modifier.size(LocalRemoteMetrics.current.iconDp)
        )
    }
}
