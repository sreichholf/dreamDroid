package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.text.asString

/** Parsed Enigma2 payload plus HTTP failure, if any. */
data class EnigmaResponse<T>(val value: T?, val error: EnigmaHttpError? = null)

fun EnigmaHttpError?.contentError(context: Context): String =
    contentErrorText().asString(context.resources)

/** "Could not get content" plus the failure's own message on a second line. */
fun EnigmaHttpError?.contentErrorText(): UiText = UiText.Resource(
    R.string.content_error_detail,
    listOf(
        UiText.Resource(R.string.get_content_error),
        this?.failure?.userMessageText() ?: UiText.Raw("")
    )
)

fun <T> EnigmaResponse<T>.valueOrThrow(): T {
    val value = this.value
    if (value != null) {
        return value
    }
    throw EnigmaFailureException(error?.failure ?: EnigmaFailure.Parse)
}
