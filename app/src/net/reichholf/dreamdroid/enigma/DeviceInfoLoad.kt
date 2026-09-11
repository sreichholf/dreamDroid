package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

data class DeviceInfoLoadResult(
    val success: Boolean,
    val info: DeviceInfo?,
    val errorText: String?,
)

/**
 * Phase 2.7b: load typed device info without a Fragment owner.
 * Uses a dedicated [SimpleHttpClient] per call (not thread-safe; cancel does not abort I/O).
 */
suspend fun loadDeviceInfo(context: Context): DeviceInfoLoadResult {
    val http = SimpleHttpClient.getInstance()
    val info = EnigmaClient(http).getDeviceInfo()
    val success = info != null && !info.isEmpty()
    val errorText = when {
        success -> null
        http.hasError() ->
            context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
        else -> context.getString(R.string.error_parsing)
    }
    return DeviceInfoLoadResult(success, info, errorText)
}
