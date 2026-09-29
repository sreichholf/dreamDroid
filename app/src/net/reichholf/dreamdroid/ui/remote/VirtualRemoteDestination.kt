package net.reichholf.dreamdroid.ui.remote

import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.ShellTitle
import net.reichholf.dreamdroid.ui.nav.ShowShellUserMessage
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotDestination
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotReloadTrigger

/**
 * Virtual remote pad as a direct Compose NavHost destination. The page and the
 * keys live on the [viewModel]; vibration and the screenshot reload stay here.
 */
@Composable
fun VirtualRemoteDestination(
    handle: PhoneNavHandle,
    viewModel: VirtualRemoteViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellTitle(uiState.title)
    ShowShellUserMessage(uiState.userMessage, viewModel::onMessageShown)
    val screenshotReload = remember { ScreenshotReloadTrigger() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var pendingScreenshot by remember { mutableStateOf<Runnable?>(null) }
    val vibrator = remember {
        context.getSystemService(Vibrator::class.java)
    }
    val showScreenshot = LocalConfiguration.current.screenWidthDp >= 720

    fun abortScreenshotReload() {
        pendingScreenshot?.let { handler.removeCallbacks(it) }
        pendingScreenshot = null
    }

    fun scheduleScreenshotReload() {
        if (!showScreenshot) {
            return
        }
        abortScreenshotReload()
        val task = Runnable {
            screenshotReload.requestReload()
            pendingScreenshot = null
        }
        pendingScreenshot = task
        handler.postDelayed(task, 700)
    }

    LaunchedEffect(uiState.screenshotEpoch) {
        if (uiState.screenshotEpoch > 0) {
            scheduleScreenshotReload()
        }
    }

    fun onKey(keyCode: Int, longClick: Boolean) {
        if (uiState.keysBlocked) {
            return
        }
        val msec = if (longClick) 100L else 25L
        vibrator?.vibrate(
            VibrationEffect.createOneShot(msec, VibrationEffect.DEFAULT_AMPLITUDE)
        )
        viewModel.onKey(keyCode, longClick)
    }

    val toggleDescription = if (uiState.page == 1) {
        stringResource(R.string.standard)
    } else {
        stringResource(R.string.quickzap)
    }
    val toggleIcon = remember(context) {
        val typed = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.ic_menu_remote, typed, true)
        if (typed.resourceId != 0) typed.resourceId else R.drawable.ic_action_list
    }

    DisposableEffect(Unit) {
        onDispose {
            abortScreenshotReload()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showScreenshot) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenshotDestination(
                    handle = handle,
                    actionsEnabled = false,
                    setTitle = false,
                    reloadTrigger = screenshotReload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp)
                )
                VirtualRemoteScreen(
                    layout = uiState.layout,
                    playButtonAsPlayPause = uiState.playAsPlayPause,
                    onKey = ::onKey,
                    onToggleLayout = viewModel::onToggleLayout,
                    toggleIconRes = toggleIcon,
                    toggleContentDescription = toggleDescription,
                    keysBlocked = uiState.keysBlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 15.dp)
                )
            }
        } else {
            VirtualRemoteScreen(
                layout = uiState.layout,
                playButtonAsPlayPause = uiState.playAsPlayPause,
                onKey = ::onKey,
                onToggleLayout = viewModel::onToggleLayout,
                toggleIconRes = toggleIcon,
                toggleContentDescription = toggleDescription,
                keysBlocked = uiState.keysBlocked,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
