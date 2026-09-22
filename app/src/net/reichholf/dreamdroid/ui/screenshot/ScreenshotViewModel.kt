package net.reichholf.dreamdroid.ui.screenshot

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.util.GregorianCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.loadScreenshot
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Owns [ScreenshotUiState] and the raw screenshot bytes used for share and save.
 *
 * [savedStateHandle] lets viewModel() construct this class. Screenshot stores no keys.
 * The bitmap stays while this ViewModel remains on the NavBackStackEntry.
 */
class ScreenshotViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val uiState: ScreenshotUiState = ScreenshotUiState()

    var rawImage: ByteArray = ByteArray(0)
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null

    fun start(type: Int, format: Int, size: Int) {
        if (!shouldGrabScreenshot(rawImage.size)) {
            if (uiState.bitmap == null) {
                onAvailable(rawImage)
            }
            return
        }
        if (loadJob?.isActive == true) {
            return
        }
        reload(type, format, size)
    }

    fun reload(type: Int, format: Int, size: Int) {
        if (SessionConnectionHolder.shared.status.value.blocksMutations) {
            return
        }
        val app = getApplication<Application>()
        uiState.loading = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = loadScreenshot(app, buildParams(type, format, size))
            uiState.loading = false
            val bytes = result.bytes
            if (result.success && bytes != null) {
                withContext(Dispatchers.Main.immediate) {
                    onAvailable(bytes)
                }
            } else {
                errorText = result.errorText?.takeIf { it.isNotEmpty() }
                    ?: app.getString(R.string.error)
            }
        }
    }

    fun consumeError() {
        errorText = null
    }

    private fun onAvailable(bytes: ByteArray) {
        rawImage = bytes
        uiState.bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        uiState.loading = false
    }

    private fun buildParams(type: Int, format: Int, size: Int): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        when (type) {
            ScreenshotParams.TYPE_OSD -> {
                params.add(NameValuePair("o", " "))
                params.add(NameValuePair("n", " "))
            }

            ScreenshotParams.TYPE_VIDEO -> params.add(NameValuePair("v", " "))

            ScreenshotParams.TYPE_ALL -> Unit
        }
        when (format) {
            ScreenshotParams.FORMAT_JPG -> params.add(NameValuePair("format", "jpg"))
            ScreenshotParams.FORMAT_PNG -> params.add(NameValuePair("format", "png"))
        }
        if (size > 0) {
            params.add(NameValuePair("r", size.toString()))
        }
        val ts = GregorianCalendar().timeInMillis / 1000
        val shotFilename = "/tmp/dreamDroid-$ts"
        params.add(NameValuePair("filename", shotFilename))
        return params
    }
}

/** Empty image bytes are the only case that grabs on start. */
fun shouldGrabScreenshot(byteCount: Int): Boolean = byteCount == 0
