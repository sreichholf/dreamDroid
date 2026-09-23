package net.reichholf.dreamdroid.enigma

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler

/**
 * Run a SimpleResult mutation via coroutines.
 * Success requires a non-null parse with non-null "statetext" (matches the former task).
 */
fun LifecycleOwner.launchSimpleResultLoad(
    requestHandler: SimpleResultRequestHandler,
    params: List<NameValuePair>,
    profile: Profile? = null,
    onResult: (success: Boolean, result: SimpleResult, error: EnigmaHttpError?) -> Unit
): Job = lifecycleScope.launchSimpleResultLoad(requestHandler, params, profile, onResult)

fun CoroutineScope.launchSimpleResultLoad(
    requestHandler: SimpleResultRequestHandler,
    params: List<NameValuePair>,
    profile: Profile? = null,
    onResult: (success: Boolean, result: SimpleResult, error: EnigmaHttpError?) -> Unit
): Job = launch {
    val http = if (profile != null) EnigmaHttp(profile) else EnigmaHttp()
    val outcome = withContext(Dispatchers.IO) {
        simpleResultFromFetch(requestHandler.fetch(http, params)) { xml ->
            requestHandler.parseSimpleResult(xml)
        }
    }
    onResult(outcome.first, outcome.second, outcome.third)
}

/**
 * HTTP failures pass through. A parsed `state=False` is [EnigmaFailure.BoxRejected]
 * without changing success (still true when `statetext` is present).
 */
internal fun simpleResultFromFetch(
    fetched: EnigmaHttpResult,
    parse: (String) -> SimpleResult
): Triple<Boolean, SimpleResult, EnigmaHttpError?> = when (fetched) {
    is EnigmaHttpResult.Success -> {
        val parsed = parse(fetched.text)
        if (parsed.stateText != null) {
            val error =
                if (parsed.state == Python.FALSE) {
                    EnigmaHttpError(EnigmaFailure.BoxRejected(parsed.stateText.orEmpty()))
                } else {
                    null
                }
            Triple(true, parsed, error)
        } else {
            Triple(false, SimpleResult(), null)
        }
    }

    is EnigmaHttpResult.Failure -> Triple(false, SimpleResult(), fetched.error)
}
