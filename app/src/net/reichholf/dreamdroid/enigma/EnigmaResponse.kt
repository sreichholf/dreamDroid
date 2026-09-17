package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttpError

/** Parsed Enigma2 payload plus HTTP failure, if any. */
data class EnigmaResponse<T>(val value: T?, val error: EnigmaHttpError? = null)

fun EnigmaHttpError?.contentError(context: Context): String =
    context.getString(R.string.get_content_error) +
        "\n" +
        (this?.failure?.userMessage(context) ?: "")

fun <T> EnigmaResponse<T>.valueOrThrow(): T {
    val value = this.value
    if (value != null) {
        return value
    }
    throw EnigmaFailureException(error?.failure ?: EnigmaFailure.Parse)
}
