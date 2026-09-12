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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Remote
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.RemoteCommandRequestHandler
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotDestination
import net.reichholf.dreamdroid.ui.screenshot.ScreenshotReloadTrigger

/**
 * Phase 2.7d: Virtual Remote as a direct Compose NavHost destination.
 * Preserves pager page 0 = Full/Simple (quickzap=false) and page 1 = QuickZap,
 * including the historical SIMPLE_VRM default-page quirk.
 */
@Composable
fun VirtualRemoteDestination(
    hostFragment: PhoneNavHostFragment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    val playAsPlayPause = remember {
        prefs.getBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, false)
    }
    val simpleRemote = remember {
        DreamDroid.getCurrentProfile().isSimpleRemote()
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
    val baseTitle = if (quickZap) {
        context.getString(R.string.app_name_release) + "::" + context.getString(R.string.quickzap)
    } else {
        context.getString(R.string.app_name_release) + "::" + context.getString(R.string.virtual_remote)
    }

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

    fun onKey(keyCode: Int, longClick: Boolean) {
        val msec = if (longClick) 100L else 25L
        vibrator?.vibrate(
            VibrationEffect.createOneShot(msec, VibrationEffect.DEFAULT_AMPLITUDE),
        )
        val params = ArrayList<NameValuePair>().apply {
            add(NameValuePair("command", keyCode.toString()))
            add(NameValuePair("rcu", if (simpleRemote) "standard" else "advanced"))
            if (longClick) {
                add(NameValuePair("type", Remote.CLICK_TYPE_LONG))
            }
        }
        hostFragment.launchSimpleResultLoad(RemoteCommandRequestHandler(), params) { _, result, http ->
            var hasError = false
            var toastText = context.getString(R.string.get_content_error)
            val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)
            val state = result.getString(SimpleResult.KEY_STATE)
            if (stateText.isNullOrEmpty()) {
                hasError = true
            }
            if (http.hasError()) {
                toastText = toastText + "\n" + http.getErrorText(context).orEmpty()
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

    val toggleIcon = remember(context) {
        val typed = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.ic_menu_remote, typed, true)
        if (typed.resourceId != 0) typed.resourceId else R.drawable.ic_action_list
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
                    actionsEnabled = false,
                    setTitle = false,
                    reloadTrigger = screenshotReload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp),
                )
                VirtualRemoteScreen(
                    layout = layout,
                    playButtonAsPlayPause = playAsPlayPause,
                    onKey = ::onKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 15.dp),
                )
            }
        } else {
            VirtualRemoteScreen(
                layout = layout,
                playButtonAsPlayPause = playAsPlayPause,
                onKey = ::onKey,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(
            onClick = { page = if (page == 0) 1 else 0 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(40.dp),
        ) {
            Icon(
                painter = painterResource(toggleIcon),
                contentDescription = context.getString(R.string.virtual_remote),
            )
        }
    }
}
