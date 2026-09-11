package net.reichholf.dreamdroid.ui.device

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.loadDeviceInfo
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState
import net.reichholf.dreamdroid.ui.compose.DreamDroidPullRefresh

private const val KEY_SAVED_INFO = "device_info"

private val DeviceInfoNullableSaver = Saver<DeviceInfo?, Bundle>(
    save = { info ->
        Bundle().apply {
            if (info != null) {
                putSerializable(KEY_SAVED_INFO, info)
            }
        }
    },
    restore = { bundle ->
        @Suppress("DEPRECATION")
        bundle.getSerializable(KEY_SAVED_INFO) as? DeviceInfo
    },
)

/**
 * Phase 2.7b: Device Info as a direct Compose NavHost destination (no nested Fragment).
 */
@Composable
fun DeviceInfoDestination(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState = remember { DeviceInfoUiState() }
    val refresh = remember { ComposeRefreshState() }
    var info by rememberSaveable(stateSaver = DeviceInfoNullableSaver) {
        mutableStateOf<DeviceInfo?>(null)
    }
    var deviceInfoReady by rememberSaveable { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    val baseTitle = context.getString(R.string.device_info)

    fun applyInfo(deviceInfo: DeviceInfo?) {
        uiState.apply(deviceInfo) { capacity, free ->
            String.format(context.getString(R.string.hdd_capacity), capacity, free)
        }
    }

    fun setToolbarTitle(title: String) {
        val activity = context as? AppCompatActivity ?: return
        activity.title = title
    }

    fun showError(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    fun reload() {
        if (!deviceInfoReady) {
            uiState.beginLoading()
        }
        refresh.setRefreshing(true)
        setToolbarTitle(context.getString(R.string.loading))
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = loadDeviceInfo(context.applicationContext)
            refresh.setRefreshing(false)
            setToolbarTitle(baseTitle)
            if (!result.success || result.info == null) {
                if (!deviceInfoReady) {
                    applyInfo(null)
                }
                val message = result.errorText?.takeIf { it.isNotEmpty() }
                    ?: context.getString(R.string.not_available)
                showError(message)
                return@launch
            }
            deviceInfoReady = true
            info = result.info
            applyInfo(result.info)
        }
    }

    DisposableEffect(baseTitle) {
        setToolbarTitle(baseTitle)
        onDispose {
            loadJob?.cancel()
            loadJob = null
        }
    }

    LaunchedEffect(Unit) {
        if (info == null || info!!.isEmpty()) {
            reload()
        } else {
            deviceInfoReady = true
            applyInfo(info)
            setToolbarTitle(baseTitle)
        }
    }

    DreamDroidPullRefresh(
        refreshing = refresh.isRefreshing,
        onRefresh = { reload() },
        enabled = refresh.enabled,
        modifier = modifier,
    ) {
        DeviceInfoScreen(state = uiState)
    }
}
