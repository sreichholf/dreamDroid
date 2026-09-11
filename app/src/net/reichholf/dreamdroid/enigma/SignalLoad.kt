package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

data class SignalLoadResult(
    val success: Boolean,
    val signal: Signal?,
    val errorText: String?,
)

/**
 * Phase 2.7c: load typed signal without a Fragment owner.
 * Uses a dedicated [SimpleHttpClient] per call (not thread-safe; cancel does not abort I/O).
 */
suspend fun loadSignal(context: Context): SignalLoadResult {
    val http = SimpleHttpClient.getInstance()
    val signal = EnigmaClient(http).getSignal()
    val success = signal != null && !signal.isEmpty()
    val errorText = when {
        success -> null
        http.hasError() ->
            context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
        else -> context.getString(R.string.error_parsing)
    }
    return SignalLoadResult(success, signal, errorText)
}
