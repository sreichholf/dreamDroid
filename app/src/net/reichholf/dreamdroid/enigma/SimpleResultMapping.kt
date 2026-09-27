package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.Python

/**
 * Maps a web mutation reply (`e2simplexmlresult`) to an [EnigmaResponse].
 *
 * - HTTP failures pass through as [EnigmaResponse.error] with no value.
 * - A reply without `statetext` has no value and no error.
 * - A parsed `state=False` keeps its value **and** carries
 *   [EnigmaFailure.BoxRejected], so check [EnigmaResponse.error] before treating a
 *   value as success.
 */
internal fun simpleResultFromFetch(
    fetched: EnigmaHttpResult,
    parse: (String) -> SimpleResult?
): EnigmaResponse<SimpleResult> = when (fetched) {
    is EnigmaHttpResult.Success -> {
        val parsed = parse(fetched.text)
        val stateText = parsed?.stateText
        when {
            parsed == null || stateText == null -> EnigmaResponse(null)

            parsed.state == Python.FALSE ->
                EnigmaResponse(parsed, EnigmaHttpError(EnigmaFailure.BoxRejected(stateText)))

            else -> EnigmaResponse(parsed)
        }
    }

    is EnigmaHttpResult.Failure -> EnigmaResponse(null, fetched.error)
}

/** The receiver's `statetext`, else the HTTP error, else the generic content error. */
fun EnigmaResponse<SimpleResult>.userMessage(context: Context): String = mutationResultText(
    stateText = value?.stateText,
    errorText = error?.resolve(context),
    fallback = context.getString(R.string.get_content_error)
)

/**
 * Mutation copy: a non-blank box `statetext` wins (including [EnigmaFailure.BoxRejected]),
 * then a non-blank error, then [fallback].
 */
internal fun mutationResultText(stateText: String?, errorText: String?, fallback: String): String =
    when {
        !stateText.isNullOrEmpty() -> stateText
        !errorText.isNullOrEmpty() -> errorText
        else -> fallback
    }
