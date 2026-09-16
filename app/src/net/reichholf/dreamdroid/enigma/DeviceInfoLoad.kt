package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R

data class DeviceInfoLoadResult(val success: Boolean, val info: DeviceInfo?, val errorText: String?)

/** Load typed device info without a Fragment owner. */
suspend fun loadDeviceInfo(context: Context): DeviceInfoLoadResult {
    val response = EnigmaClient().getDeviceInfo()
    val info = response.value
    val success = info != null && !info.isEmpty()
    val errorText = when {
        success -> null
        response.error != null -> response.error.contentError(context)
        else -> context.getString(R.string.error_parsing)
    }
    return DeviceInfoLoadResult(success, info, errorText)
}
