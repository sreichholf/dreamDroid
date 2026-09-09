package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

/**
 * Phase 2.2f: load typed hub now/next rows via coroutines (no executor / runBlocking).
 * Uses a dedicated [SimpleHttpClient] per load (same as GetEpgNowNextTask).
 * Success is "!http.hasError()" — empty lists are success (matches the former task).
 */
fun Fragment.launchEpgNowNextLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, rows: List<ServiceNowNext>, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val uri = if (DreamDroid.featureNowNext()) URIStore.EPG_NOWNEXT else URIStore.EPG_NOW
        val rows = EnigmaClient(http).getEpgNowNext(params, uri)
        if (!isAdded) {
            return@launch
        }
        val success = !http.hasError()
        val errorText = if (success) {
            null
        } else {
            getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
        }
        onResult(success, rows, errorText)
    }
}
