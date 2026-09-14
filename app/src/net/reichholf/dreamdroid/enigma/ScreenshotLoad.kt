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

private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
private val PNG_MAGIC = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)

/**
 * True when [bytes] start with JPEG SOI or a PNG signature, not HTML/XML/text.
 */
internal fun looksLikeScreenshotImage(bytes: ByteArray): Boolean {
    return hasMagic(bytes, JPEG_MAGIC) || hasMagic(bytes, PNG_MAGIC)
}

internal fun screenshotPayloadResult(
    bytes: ByteArray,
    httpErrorText: String?,
    fallbackError: String,
): ScreenshotLoadResult {
    val success = looksLikeScreenshotImage(bytes)
    val errorText = when {
        success -> null
        !httpErrorText.isNullOrEmpty() -> httpErrorText
        else -> fallbackError
    }
    return ScreenshotLoadResult(success, if (success) bytes else null, errorText)
}

private fun hasMagic(bytes: ByteArray, magic: ByteArray): Boolean {
    if (bytes.size < magic.size) {
        return false
    }
    for (i in magic.indices) {
        if (bytes[i] != magic[i]) {
            return false
        }
    }
    return true
}

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
    val httpErrorText = if (http.hasError()) http.getErrorText(context) else null
    return screenshotPayloadResult(bytes, httpErrorText, context.getString(R.string.error))
}
