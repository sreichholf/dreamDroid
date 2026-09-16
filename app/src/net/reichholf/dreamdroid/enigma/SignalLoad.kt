package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R

data class SignalLoadResult(val success: Boolean, val signal: Signal?, val errorText: String?)

/** Load typed signal without a Fragment owner. */
suspend fun loadSignal(context: Context): SignalLoadResult {
    val response = EnigmaClient().getSignal()
    val signal = response.value
    val success = signal != null && !signal.isEmpty()
    val errorText = when {
        success -> null
        response.error != null -> response.error.contentError(context)
        else -> context.getString(R.string.error_parsing)
    }
    return SignalLoadResult(success, signal, errorText)
}
