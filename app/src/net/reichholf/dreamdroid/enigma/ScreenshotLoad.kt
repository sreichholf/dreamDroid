package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.Request
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import java.util.ArrayList

data class ScreenshotLoadResult(
    val success: Boolean,
    val bytes: ByteArray?,
    val errorText: String?,
)

/**
 * Phase 2.7c: load screenshot bytes without a Fragment owner.
 * Also used by [ScreenShotFragment] (Virtual Remote embed) via [launchScreenshotLoad].
 */
suspend fun loadScreenshot(
    context: Context,
    params: List<NameValuePair>,
): ScreenshotLoadResult {
    val http = SimpleHttpClient.getInstance()
    val bytes = withContext(Dispatchers.IO) {
        Request.getBytes(http, URIStore.SCREENSHOT, ArrayList(params))
    }
    val success = bytes.isNotEmpty()
    val errorText = when {
        success -> null
        http.hasError() -> http.getErrorText(context)
        else -> context.getString(R.string.error)
    }
    return ScreenshotLoadResult(success, if (success) bytes else null, errorText)
}

fun Fragment.launchScreenshotLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, bytes: ByteArray?, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val result = loadScreenshot(requireContext(), params)
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.bytes, result.errorText)
    }
}
