package net.reichholf.dreamdroid.enigma

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair
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
): Job = lifecycleScope.launch {
    val http = if (profile != null) EnigmaHttp(profile) else EnigmaHttp()
    val outcome = withContext(Dispatchers.IO) {
        when (val fetched = requestHandler.fetch(http, params)) {
            is EnigmaHttpResult.Success -> {
                val parsed = requestHandler.parseSimpleResult(fetched.text)
                if (parsed.stateText != null) {
                    Triple(true, parsed, null as EnigmaHttpError?)
                } else {
                    Triple(false, SimpleResult(), null)
                }
            }

            is EnigmaHttpResult.Failure ->
                Triple(false, SimpleResult(), fetched.error)
        }
    }
    onResult(outcome.first, outcome.second, outcome.third)
}
