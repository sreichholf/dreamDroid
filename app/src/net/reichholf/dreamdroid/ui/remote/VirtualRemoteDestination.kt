package net.reichholf.dreamdroid.ui.remote

import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
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
    val showScreenshot = showsRemoteScreenshot()

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

    val pad: @Composable (Modifier) -> Unit = { padModifier ->
        VirtualRemoteScreen(
            layout = uiState.layout,
            playButtonAsPlayPause = uiState.playAsPlayPause,
            onKey = ::onKey,
            onToggleLayout = viewModel::onToggleLayout,
            toggleIconRes = toggleIcon,
            toggleContentDescription = toggleDescription,
            keysBlocked = uiState.keysBlocked,
            modifier = padModifier
        )
    }
    if (showScreenshot) {
        RemoteWithScreenshot(
            screenshot = { screenshotModifier ->
                ScreenshotDestination(
                    handle = handle,
                    actionsEnabled = false,
                    setTitle = false,
                    reloadTrigger = screenshotReload,
                    modifier = screenshotModifier
                )
            },
            pad = pad,
            modifier = modifier
        )
    } else {
        pad(modifier.fillMaxSize())
    }
}

/**
 * The receiver screenshot beside the pad on expanded width, above it otherwise. The pad fills
 * whatever it is given, so both are weighted; unweighted, the pad would leave the screenshot
 * no height.
 */
@Composable
internal fun RemoteWithScreenshot(
    screenshot: @Composable (Modifier) -> Unit,
    pad: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    if (expanded) {
        Row(modifier.fillMaxSize()) {
            screenshot(Modifier.weight(1f).fillMaxHeight().padding(20.dp))
            pad(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize()) {
            screenshot(Modifier.weight(2f).fillMaxWidth().padding(20.dp))
            pad(Modifier.weight(3f).fillMaxWidth().padding(bottom = 15.dp))
        }
    }
}

/**
 * Whether the receiver screenshot fits next to the pad: beside it on an expanded width with
 * medium height (tablets in landscape, open foldables), or above it on a medium width with
 * expanded height (tablets upright). Phones and smaller windows keep the pad alone.
 */
@Composable
internal fun showsRemoteScreenshot(): Boolean {
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return sizeClass.isAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
    ) ||
        sizeClass.isAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
            WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND
        )
}
