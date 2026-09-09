package net.reichholf.dreamdroid.ui.screenshot

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.chrisbanes.photoview.PhotoView
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme

class ScreenshotUiState {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var actionsEnabled by mutableStateOf(true)
    var loading by mutableStateOf(false)
}

@Composable
fun ScreenshotScreen(
    state: ScreenshotUiState,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenshotLabel = stringResource(R.string.screenshot)
    val reloadLabel = stringResource(R.string.reload)
    val shareLabel = stringResource(R.string.share)
    val saveLabel = stringResource(R.string.save)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (state.actionsEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onReload) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_refresh),
                        contentDescription = reloadLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_share),
                        contentDescription = shareLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onSave) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_save),
                        contentDescription = saveLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            AndroidView(
                factory = { context ->
                    PhotoView(context).apply {
                        setBackgroundColor(AndroidColor.BLACK)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = screenshotLabel },
                update = { photoView ->
                    val bmp = state.bitmap
                    if (bmp != null) {
                        photoView.setImageBitmap(bmp)
                        photoView.attacher.update()
                    } else {
                        photoView.setImageDrawable(null)
                    }
                },
            )

            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

fun ComposeView.bindScreenshotScreen(
    state: ScreenshotUiState,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        DreamDroidTheme {
            ScreenshotScreen(
                state = state,
                onReload = onReload,
                onShare = onShare,
                onSave = onSave,
            )
        }
    }
}
