package net.reichholf.dreamdroid.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly

/**
 * Optional external reload trigger for embeds (Virtual Remote tablet pane).
 */
class ScreenshotReloadTrigger {
    var tick by mutableIntStateOf(0)
        private set

    fun requestReload() {
        tick++
    }
}

/**
 * Screenshot as a NavHost destination (also a Tools hub tab). Virtual Remote embeds it on
 * large screens with [setTitle] and [actionsEnabled] false and reloads it via
 * [reloadTrigger]. Saving and sharing need a `Context`, so they run here and report back to
 * the ViewModel for the message.
 */
@Composable
fun ScreenshotDestination(
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    setTitle: Boolean = true,
    reloadTrigger: ScreenshotReloadTrigger? = null,
    handle: PhoneNavHandle? = null,
    viewModel: ScreenshotViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (setTitle) {
        ShellTitle(uiState.title)
    }
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)

    val triggerTick = reloadTrigger?.tick ?: 0
    LaunchedEffect(triggerTick) {
        if (triggerTick > 0) {
            viewModel.refresh()
        }
    }

    ScreenshotScreen(
        state = uiState,
        actionsEnabled = actionsEnabled,
        onReload = {
            if (handle != null) {
                handle.runOnlineOnly(viewModel::refresh)
            } else {
                viewModel.refresh()
            }
        },
        onShare = {
            val image = uiState.image
            if (image == null || !shareScreenshot(context, image)) {
                viewModel.onFileFailed()
            }
        },
        onSave = {
            val fileName = uiState.image?.let { saveScreenshotToGallery(context, it) }
            if (fileName == null) {
                viewModel.onFileFailed()
            } else {
                viewModel.onSaved(fileName)
            }
        },
        modifier = modifier
    )
}
