package net.reichholf.dreamdroid.ui.remote

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun VirtualRemoteScreen(
    layout: VirtualRemoteLayout,
    playButtonAsPlayPause: Boolean,
    onKey: (keyCode: Int, longClick: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RemoteKey(label = "Help", keyCode = Remote.KEY_HELP, onKey = onKey, height = 36.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoteKey(label = "V+", keyCode = Remote.KEY_VOLP, onKey = onKey, container = KeyLight)
            RemoteKey(label = "V-", keyCode = Remote.KEY_VOLM, onKey = onKey, container = KeyLight)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoteKey(label = "Mute", keyCode = Remote.KEY_MUTE, onKey = onKey)
            RemoteKey(label = "Exit", keyCode = Remote.KEY_EXIT, onKey = onKey, container = KeyRed)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoteKey(label = "B+", keyCode = Remote.KEY_BOUP, onKey = onKey, container = KeyLight)
            RemoteKey(label = "B-", keyCode = Remote.KEY_BOUM, onKey = onKey, container = KeyLight)
        }
        RemoteKey(label = "PWR", keyCode = Remote.KEY_POWER, onKey = onKey, container = KeyRed, height = 36.dp)
    }
    NavigationPad(onKey = onKey, big = true)
    ColorKeysRow(onKey = onKey, height = 36.dp)
}

@Composable
private fun NumberVolumeBouquetPad(onKey: (Int, Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoteKey(label = "Help", keyCode = Remote.KEY_HELP, onKey = onKey, height = 36.dp)
            RemoteKey(label = "V+", keyCode = Remote.KEY_VOLP, onKey = onKey, container = KeyLight)
            RemoteKey(label = "V-", keyCode = Remote.KEY_VOLM, onKey = onKey, container = KeyLight)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DigitKey("1", Remote.KEY_1, onKey)
                DigitKey("2", Remote.KEY_2, onKey)
                DigitKey("3", Remote.KEY_3, onKey)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DigitKey("4", Remote.KEY_4, onKey)
                DigitKey("5", Remote.KEY_5, onKey)
                DigitKey("6", Remote.KEY_6, onKey)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DigitKey("7", Remote.KEY_7, onKey)
                DigitKey("8", Remote.KEY_8, onKey)
                DigitKey("9", Remote.KEY_9, onKey)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DigitKey("<", Remote.KEY_PREV, onKey)
                DigitKey("0", Remote.KEY_0, onKey)
                DigitKey(">", Remote.KEY_NEXT, onKey)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RemoteKey(label = "PWR", keyCode = Remote.KEY_POWER, onKey = onKey, container = KeyRed, height = 36.dp)
            RemoteKey(label = "B+", keyCode = Remote.KEY_BOUP, onKey = onKey, container = KeyLight)
            RemoteKey(label = "B-", keyCode = Remote.KEY_BOUM, onKey = onKey, container = KeyLight)
        }
    }
}

@Composable
private fun ColorKeysRow(onKey: (Int, Boolean) -> Unit, height: Dp = 30.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RemoteKey(label = "Red", keyCode = Remote.KEY_RED, onKey = onKey, container = KeyRed, height = height, showLabel = false)
        RemoteKey(label = "Green", keyCode = Remote.KEY_GREEN, onKey = onKey, container = KeyGreen, height = height, showLabel = false)
        RemoteKey(label = "Yellow", keyCode = Remote.KEY_YELLOW, onKey = onKey, container = KeyYellow, height = height, showLabel = false, contentColor = KeyOnYellow)
        RemoteKey(label = "Blue", keyCode = Remote.KEY_BLUE, onKey = onKey, container = KeyBlue, height = height, showLabel = false)
    }
}

@Composable
private fun NavigationPad(onKey: (Int, Boolean) -> Unit, big: Boolean = false) {
    val size = if (big) 64.dp else 52.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RemoteKey(label = "Mute", keyCode = Remote.KEY_MUTE, onKey = onKey, width = 88.dp, height = 36.dp)
        RemoteKey(label = "Exit", keyCode = Remote.KEY_EXIT, onKey = onKey, width = 88.dp, height = 36.dp, container = KeyRed)
    }
}

@Composable
private fun TransportPad(playButtonAsPlayPause: Boolean, onKey: (Int, Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconRemoteKey(
            description = "Rewind",
            keyCode = Remote.KEY_REWIND,
            iconRes = R.drawable.ic_media_previous_dark,
            onKey = onKey,
            width = 56.dp,
            height = 48.dp,
        )
        if (playButtonAsPlayPause) {
            IconRemoteKey(
                description = "Play/Pause",
                keyCode = Remote.KEY_PLAYPAUSE,
                iconRes = R.drawable.ic_media_play_pause_dark,
                onKey = onKey,
                width = 56.dp,
                height = 48.dp,
            )
        } else {
            IconRemoteKey(
                description = "Play",
                keyCode = Remote.KEY_PLAY,
                iconRes = R.drawable.ic_media_play_dark,
                onKey = onKey,
                width = 56.dp,
                height = 48.dp,
            )
        }
        IconRemoteKey(
            description = "Stop",
            keyCode = Remote.KEY_STOP,
            iconRes = R.drawable.ic_media_stop_dark,
            onKey = onKey,
            width = 56.dp,
            height = 48.dp,
        )
        IconRemoteKey(
            description = "Forward",
            keyCode = Remote.KEY_FORWARD,
            iconRes = R.drawable.ic_media_next_dark,
            onKey = onKey,
            width = 56.dp,
            height = 48.dp,
        )
    }
}

@Composable
private fun SourcePad(onKey: (Int, Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RemoteKey(label = "TV", keyCode = Remote.KEY_TV, onKey = onKey, width = 56.dp, height = 48.dp)
        RemoteKey(label = "RADIO", keyCode = Remote.KEY_RADIO, onKey = onKey, width = 56.dp, height = 48.dp)
        RemoteKey(label = "TEXT", keyCode = Remote.KEY_TEXT, onKey = onKey, width = 56.dp, height = 48.dp)
        RemoteKey(
            label = "REC",
            keyCode = Remote.KEY_RECORD,
            onKey = onKey,
            width = 56.dp,
            height = 48.dp,
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
    width: Dp = 52.dp,
    height: Dp = 48.dp,
    container: Color = KeyDark,
    contentColor: Color = KeyOnDark,
    showLabel: Boolean = true,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
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
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = KeyOnDark,
            modifier = Modifier.size(24.dp),
        )
    }
}
