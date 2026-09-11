package net.reichholf.dreamdroid.enigma

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
 * Phase 2.7c/d: load screenshot bytes without a Fragment owner.
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
