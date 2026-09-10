package net.reichholf.dreamdroid.ui.remote

import android.content.res.ColorStateList
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.ImageViewCompat
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.Remote

enum class VirtualRemoteLayout {
    Full,
    Simple,
    QuickZap,
}

private val KeyDark = Color(0xFF424242)
private val KeyLight = Color(0xFF757575)
private val KeyRed = Color(0xFFC62828)
private val KeyGreen = Color(0xFF2E7D32)
private val KeyYellow = Color(0xFFF9A825)
private val KeyBlue = Color(0xFF1565C0)
private val KeyOnDark = Color.White
private val KeyOnYellow = Color(0xFF212121)

private data class RemoteMetrics(
    val keyWidth: Dp,
    val keyHeight: Dp,
    val keyHeightLow: Dp,
    val gap: Dp,
)

private val LocalRemoteMetrics = compositionLocalOf {
    RemoteMetrics(keyWidth = 56.dp, keyHeight = 48.dp, keyHeightLow = 36.dp, gap = 4.dp)
}

@Composable
fun VirtualRemoteScreen(
    layout: VirtualRemoteLayout,
    playButtonAsPlayPause: Boolean,
    onKey: (keyCode: Int, longClick: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val horizontalPad = 16.dp
        val available = maxWidth - horizontalPad * 2
        // Full/simple pads are five columns (side + 3 digits + side); quick-zap is also five.
        val gap = 4.dp
        val rawKey = (available - gap * 4) / 5
        val keyWidth = rawKey.coerceIn(52.dp, 72.dp)
        val keyHeight = (keyWidth * 0.86f).coerceIn(44.dp, 64.dp)
        val keyHeightLow = (keyHeight * 0.75f).coerceIn(32.dp, 48.dp)
        val metrics = RemoteMetrics(
            keyWidth = keyWidth,
            keyHeight = keyHeight,
            keyHeightLow = keyHeightLow,
            gap = gap,
        )
        val padMaxWidth = keyWidth * 5 + gap * 4

        CompositionLocalProvider(LocalRemoteMetrics provides metrics) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = padMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPad, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(metrics.gap * 2 + 2.dp),
            ) {
                when (layout) {
                    VirtualRemoteLayout.QuickZap -> QuickZapPad(onKey = onKey)
                    VirtualRemoteLayout.Simple -> SimplePad(onKey = onKey)
                    VirtualRemoteLayout.Full -> FullPad(
                        playButtonAsPlayPause = playButtonAsPlayPause,
                        onKey = onKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun FullPad(
    playButtonAsPlayPause: Boolean,
    onKey: (Int, Boolean) -> Unit,
) {
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
            RemoteKey(label = "V+", keyCode = Remote.KEY_VOLP, onKey = onKey, container = KeyLight)
            RemoteKey(label = "V-", keyCode = Remote.KEY_VOLM, onKey = onKey, container = KeyLight)
        }
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(label = "Mute", keyCode = Remote.KEY_MUTE, onKey = onKey)
            RemoteKey(label = "Exit", keyCode = Remote.KEY_EXIT, onKey = onKey, container = KeyRed)
        }
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(label = "B+", keyCode = Remote.KEY_BOUP, onKey = onKey, container = KeyLight)
            RemoteKey(label = "B-", keyCode = Remote.KEY_BOUM, onKey = onKey, container = KeyLight)
        }
        RemoteKey(label = "PWR", keyCode = Remote.KEY_POWER, onKey = onKey, container = KeyRed, height = m.keyHeightLow)
    }
    NavigationPad(onKey = onKey, big = true)
    ColorKeysRow(onKey = onKey, height = m.keyHeightLow)
}

@Composable
private fun NumberVolumeBouquetPad(onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(label = "Help", keyCode = Remote.KEY_HELP, onKey = onKey, height = m.keyHeightLow)
            RemoteKey(label = "V+", keyCode = Remote.KEY_VOLP, onKey = onKey, container = KeyLight)
            RemoteKey(label = "V-", keyCode = Remote.KEY_VOLM, onKey = onKey, container = KeyLight)
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
            RemoteKey(label = "PWR", keyCode = Remote.KEY_POWER, onKey = onKey, container = KeyRed, height = m.keyHeightLow)
            RemoteKey(label = "B+", keyCode = Remote.KEY_BOUP, onKey = onKey, container = KeyLight)
            RemoteKey(label = "B-", keyCode = Remote.KEY_BOUM, onKey = onKey, container = KeyLight)
        }
    }
}

@Composable
private fun ColorKeysRow(onKey: (Int, Boolean) -> Unit, height: Dp? = null) {
    val m = LocalRemoteMetrics.current
    val rowHeight = height ?: m.keyHeightLow
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        RemoteKey(label = "Red", keyCode = Remote.KEY_RED, onKey = onKey, container = KeyRed, height = rowHeight, showLabel = false)
        RemoteKey(label = "Green", keyCode = Remote.KEY_GREEN, onKey = onKey, container = KeyGreen, height = rowHeight, showLabel = false)
        RemoteKey(label = "Yellow", keyCode = Remote.KEY_YELLOW, onKey = onKey, container = KeyYellow, height = rowHeight, showLabel = false, contentColor = KeyOnYellow)
        RemoteKey(label = "Blue", keyCode = Remote.KEY_BLUE, onKey = onKey, container = KeyBlue, height = rowHeight, showLabel = false)
    }
}

@Composable
private fun NavigationPad(onKey: (Int, Boolean) -> Unit, big: Boolean = false) {
    val m = LocalRemoteMetrics.current
    val size = if (big) (m.keyWidth * 1.15f).coerceAtMost(72.dp) else m.keyWidth
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(m.gap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(label = "Info", keyCode = Remote.KEY_INFO, onKey = onKey, width = size, height = size)
            IconRemoteKey(
                description = "Up",
                keyCode = Remote.KEY_UP,
                iconRes = R.drawable.ic_key_up,
                onKey = onKey,
                width = size,
                height = size,
                container = KeyLight,
            )
            RemoteKey(label = "Menu", keyCode = Remote.KEY_MENU, onKey = onKey, width = size, height = size)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            IconRemoteKey(
                description = "Left",
                keyCode = Remote.KEY_LEFT,
                iconRes = R.drawable.ic_key_left,
                onKey = onKey,
                width = size,
                height = size,
                container = KeyLight,
            )
            RemoteKey(label = "OK", keyCode = Remote.KEY_OK, onKey = onKey, width = size, height = size, container = KeyLight)
            IconRemoteKey(
                description = "Right",
                keyCode = Remote.KEY_RIGHT,
                iconRes = R.drawable.ic_key_right,
                onKey = onKey,
                width = size,
                height = size,
                container = KeyLight,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            RemoteKey(label = "Audio", keyCode = Remote.KEY_AUDIO, onKey = onKey, width = size, height = size)
            IconRemoteKey(
                description = "Down",
                keyCode = Remote.KEY_DOWN,
                iconRes = R.drawable.ic_key_down,
                onKey = onKey,
                width = size,
                height = size,
                container = KeyLight,
            )
            RemoteKey(label = "PVR", keyCode = Remote.KEY_PVR, onKey = onKey, width = size, height = size)
        }
    }
}

@Composable
private fun MuteExitRow(onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    val width = m.keyWidth * 1.7f
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        RemoteKey(label = "Mute", keyCode = Remote.KEY_MUTE, onKey = onKey, width = width, height = m.keyHeightLow)
        RemoteKey(label = "Exit", keyCode = Remote.KEY_EXIT, onKey = onKey, width = width, height = m.keyHeightLow, container = KeyRed)
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
            height = m.keyHeight,
        )
        if (playButtonAsPlayPause) {
            IconRemoteKey(
                description = "Play/Pause",
                keyCode = Remote.KEY_PLAYPAUSE,
                iconRes = R.drawable.ic_media_play_pause_dark,
                onKey = onKey,
                width = m.keyWidth,
                height = m.keyHeight,
            )
        } else {
            IconRemoteKey(
                description = "Play",
                keyCode = Remote.KEY_PLAY,
                iconRes = R.drawable.ic_media_play_dark,
                onKey = onKey,
                width = m.keyWidth,
                height = m.keyHeight,
            )
        }
        IconRemoteKey(
            description = "Stop",
            keyCode = Remote.KEY_STOP,
            iconRes = R.drawable.ic_media_stop_dark,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight,
        )
        IconRemoteKey(
            description = "Forward",
            keyCode = Remote.KEY_FORWARD,
            iconRes = R.drawable.ic_media_next_dark,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight,
        )
    }
}

@Composable
private fun SourcePad(onKey: (Int, Boolean) -> Unit) {
    val m = LocalRemoteMetrics.current
    Row(horizontalArrangement = Arrangement.spacedBy(m.gap)) {
        RemoteKey(label = "TV", keyCode = Remote.KEY_TV, onKey = onKey, width = m.keyWidth, height = m.keyHeight)
        RemoteKey(label = "RADIO", keyCode = Remote.KEY_RADIO, onKey = onKey, width = m.keyWidth, height = m.keyHeight)
        RemoteKey(label = "TEXT", keyCode = Remote.KEY_TEXT, onKey = onKey, width = m.keyWidth, height = m.keyHeight)
        RemoteKey(
            label = "REC",
            keyCode = Remote.KEY_RECORD,
            onKey = onKey,
            width = m.keyWidth,
            height = m.keyHeight,
            contentColor = KeyRed,
        )
    }
}

@Composable
private fun DigitKey(label: String, keyCode: Int, onKey: (Int, Boolean) -> Unit) {
    RemoteKey(
        label = label,
        keyCode = keyCode,
        onKey = onKey,
        fontWeight = FontWeight.Bold,
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
    container: Color = KeyDark,
    contentColor: Color = KeyOnDark,
    showLabel: Boolean = true,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val m = LocalRemoteMetrics.current
    val keyWidth = width ?: m.keyWidth
    val keyHeight = height ?: m.keyHeight
    Box(
        modifier = modifier
            .width(keyWidth)
            .height(keyHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .semantics { contentDescription = label }
            .combinedClickable(
                onClick = { onKey(keyCode, false) },
                onLongClick = { onKey(keyCode, true) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (showLabel) {
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                maxLines = 1,
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
    container: Color = KeyDark,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .semantics { contentDescription = description }
            .combinedClickable(
                onClick = { onKey(keyCode, false) },
                onLongClick = { onKey(keyCode, true) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // layer-list / rotate drawables are not VectorDrawables; painterResource cannot load them.
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageResource(iconRes)
                    ImageViewCompat.setImageTintList(
                        this,
                        ColorStateList.valueOf(KeyOnDark.toArgb()),
                    )
                }
            },
            update = { imageView ->
                imageView.setImageResource(iconRes)
                ImageViewCompat.setImageTintList(
                    imageView,
                    ColorStateList.valueOf(KeyOnDark.toArgb()),
                )
            },
            modifier = Modifier.size(24.dp),
        )
    }
}
