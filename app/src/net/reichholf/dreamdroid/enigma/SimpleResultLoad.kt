package net.reichholf.dreamdroid.enigma

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler
import java.util.ArrayList

/**
 * Phase 2.2k: run a SimpleResult mutation via coroutines (no executor).
 * Dedicated [SimpleHttpClient] per call (same as SimpleResultTask).
 * Success requires a non-null parse with non-null "statetext" (matches the former task).
 */
fun LifecycleOwner.launchSimpleResultLoad(
    requestHandler: SimpleResultRequestHandler,
    params: List<NameValuePair>,
    onResult: (success: Boolean, result: ExtendedHashMap, http: SimpleHttpClient) -> Unit,
): Job {
    return lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val pair = withContext(Dispatchers.IO) {
            val xml = requestHandler.get(http, ArrayList(params))
            if (xml != null) {
                val parsed = requestHandler.parseSimpleResult(xml)
                val stateText = parsed.getString("statetext")
                if (stateText != null) {
                    true to parsed
                } else {
                    false to ExtendedHashMap()
                }
            } else {
                false to ExtendedHashMap()
            }
        }
        onResult(pair.first, pair.second, http)
    }
}
