package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.Python

/**
 * Outcome of a web mutation that answers with `e2simplexmlresult`.
 * [success] needs a parsed [SimpleResult] with a non-null `statetext`.
 */
data class SimpleResultResponse(
    val success: Boolean,
    val result: SimpleResult,
    val error: EnigmaHttpError?
)

/**
 * HTTP failures pass through. A parsed `state=False` is [EnigmaFailure.BoxRejected]
 * without changing success (still true when `statetext` is present).
 */
internal fun simpleResultFromFetch(
    fetched: EnigmaHttpResult,
    parse: (String) -> SimpleResult?
): SimpleResultResponse = when (fetched) {
    is EnigmaHttpResult.Success -> {
        val parsed = parse(fetched.text) ?: SimpleResult(state = Python.FALSE)
        if (parsed.stateText != null) {
            val error =
                if (parsed.state == Python.FALSE) {
                    EnigmaHttpError(EnigmaFailure.BoxRejected(parsed.stateText))
                } else {
                    null
                }
            SimpleResultResponse(true, parsed, error)
        } else {
            SimpleResultResponse(false, SimpleResult(), null)
        }
    }

    is EnigmaHttpResult.Failure -> SimpleResultResponse(false, SimpleResult(), fetched.error)
}

/** The receiver's `statetext`, else the HTTP error, else the generic content error. */
fun SimpleResultResponse.userMessage(context: Context): String {
    val stateText = result.stateText
    val errorText = error?.resolve(context)
    return when {
        !stateText.isNullOrEmpty() -> stateText
        !errorText.isNullOrEmpty() -> errorText
        else -> context.getString(R.string.get_content_error)
    }
}
