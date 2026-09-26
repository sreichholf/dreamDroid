package net.reichholf.dreamdroid.ui.remote

import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Remote
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.RemoteCommandRequestHandler
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.launchSimpleResultLoad
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotDestination
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotReloadTrigger

/**
 * Phase 2.7d: Virtual Remote as a direct Compose NavHost destination.
 * Preserves pager page 0 = Full/Simple (quickzap=false) and page 1 = QuickZap,
 * including the historical SIMPLE_VRM default-page quirk.
 */
@Composable
fun VirtualRemoteDestination(handle: PhoneNavHandle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    val playAsPlayPause = remember {
        prefs.getBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, false)
    }
    val simpleRemote = remember {
        ProfileRepository.get().requireCurrent().simpleRemote
    }
    val defaultPage = remember {
        if (!prefs.getBoolean(DreamDroid.PREFS_KEY_SIMPLE_VRM, true)) 1 else 0
    }
    var page by rememberSaveable { mutableIntStateOf(defaultPage) }
    val screenshotReload = remember { ScreenshotReloadTrigger() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var pendingScreenshot by remember { mutableStateOf<Runnable?>(null) }
    val vibrator = remember {
        context.getSystemService(Vibrator::class.java)
    }
    val showScreenshot = LocalConfiguration.current.screenWidthDp >= 720

    val quickZap = page == 1
    val layout = when {
        quickZap -> VirtualRemoteLayout.QuickZap
        simpleRemote -> VirtualRemoteLayout.Simple
        else -> VirtualRemoteLayout.Full
    }
    val baseTitle = virtualRemoteToolbarTitle(
        quickZap = quickZap,
        virtualRemote = stringResource(R.string.virtual_remote),
        quickZapLabel = stringResource(R.string.quickzap)
    )

    fun setToolbarTitle() {
        (context as? AppCompatActivity)?.title = baseTitle
    }

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

    val contentErrorText = stringResource(R.string.get_content_error)

    fun onKey(keyCode: Int, longClick: Boolean) {
        handle.runOnlineOnly {
            val msec = if (longClick) 100L else 25L
            vibrator?.vibrate(
                VibrationEffect.createOneShot(msec, VibrationEffect.DEFAULT_AMPLITUDE)
            )
            val params = ArrayList<NameValuePair>().apply {
                add(NameValuePair("command", keyCode.toString()))
                add(NameValuePair("rcu", if (simpleRemote) "standard" else "advanced"))
                if (longClick) {
                    add(NameValuePair("type", Remote.CLICK_TYPE_LONG))
                }
            }
            handle.launchSimpleResultLoad(RemoteCommandRequestHandler(), params) {
                    _,
                    result,
                    error
                ->
                var hasError = false
                var toastText = contentErrorText
                val stateText = result.stateText
                val state = result.state
                if (stateText.isNullOrEmpty()) {
                    hasError = true
                }
                if (error != null) {
                    toastText = toastText + "\n" + error.resolve(context).orEmpty()
                    hasError = true
                } else if (Python.FALSE == state) {
                    hasError = true
                    toastText = stateText ?: toastText
                }
                if (hasError) {
                    Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
                } else {
                    scheduleScreenshotReload()
                }
            }
        }
    }

    val status by handle.connectionStatusFlow().collectAsState()
    val keysBlocked = status.blocksMutations
    val toggleIcon = remember(context) {
        val typed = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.ic_menu_remote, typed, true)
        if (typed.resourceId != 0) typed.resourceId else R.drawable.ic_action_list
    }
    val toggleDescription = if (quickZap) {
        stringResource(R.string.standard)
    } else {
        stringResource(R.string.quickzap)
    }

    DisposableEffect(baseTitle) {
        setToolbarTitle()
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
                    layout = layout,
                    playButtonAsPlayPause = playAsPlayPause,
                    onKey = ::onKey,
                    onToggleLayout = { page = if (page == 0) 1 else 0 },
                    toggleIconRes = toggleIcon,
                    toggleContentDescription = toggleDescription,
                    keysBlocked = keysBlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 15.dp)
                )
            }
        } else {
            VirtualRemoteScreen(
                layout = layout,
                playButtonAsPlayPause = playAsPlayPause,
                onKey = ::onKey,
                onToggleLayout = { page = if (page == 0) 1 else 0 },
                toggleIconRes = toggleIcon,
                toggleContentDescription = toggleDescription,
                keysBlocked = keysBlocked,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Toolbar label for the pad. Feature name only, no `DreamDroid::` prefix. */
internal fun virtualRemoteToolbarTitle(
    quickZap: Boolean,
    virtualRemote: String,
    quickZapLabel: String
): String = if (quickZap) quickZapLabel else virtualRemote
