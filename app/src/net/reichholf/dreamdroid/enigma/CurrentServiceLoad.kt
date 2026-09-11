package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

data class CurrentServiceLoadResult(
    val success: Boolean,
    val current: CurrentService?,
    val errorText: String?,
)

/**
 * Phase 2.7c: load typed current service without a Fragment owner.
 * Dedicated [SimpleHttpClient] per call (cancel does not abort I/O).
 */
suspend fun loadCurrentService(context: Context): CurrentServiceLoadResult {
    val http = SimpleHttpClient.getInstance()
    val current = EnigmaClient(http).getCurrent()
    val success = current != null
    val errorText = when {
        success -> null
        http.hasError() ->
            context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
        else -> context.getString(R.string.error_parsing)
    }
    return CurrentServiceLoadResult(success, current, errorText)
}
