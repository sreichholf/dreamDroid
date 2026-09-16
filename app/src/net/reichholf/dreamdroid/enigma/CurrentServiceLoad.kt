package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R

data class CurrentServiceLoadResult(
    val success: Boolean,
    val current: CurrentService?,
    val errorText: String?
)

/** Load typed current service without a Fragment owner. */
suspend fun loadCurrentService(context: Context): CurrentServiceLoadResult {
    val response = EnigmaClient().getCurrent()
    val current = response.value
    val success = current != null
    val errorText = when {
        success -> null
        response.error != null -> response.error.contentError(context)
        else -> context.getString(R.string.error_parsing)
    }
    return CurrentServiceLoadResult(success, current, errorText)
}
