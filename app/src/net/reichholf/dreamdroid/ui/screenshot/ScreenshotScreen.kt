package net.reichholf.dreamdroid.ui.screenshot

import android.graphics.Bitmap
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.session.onlineOnlyLook

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
    grabBlocked: Boolean = false
) {
    val screenshotLabel = stringResource(R.string.screenshot)
    val reloadLabel = stringResource(R.string.reload)
    val shareLabel = stringResource(R.string.share)
    val saveLabel = stringResource(R.string.save)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
    ) {
        if (state.actionsEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onReload,
                    modifier = Modifier.onlineOnlyLook(grabBlocked)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_refresh),
                        contentDescription = reloadLabel,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_share),
                        contentDescription = shareLabel,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onSave) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_save),
                        contentDescription = saveLabel,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ZoomableScreenshot(
                bitmap = state.bitmap,
                contentDescription = screenshotLabel,
                modifier = Modifier.fillMaxSize()
            )

            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
