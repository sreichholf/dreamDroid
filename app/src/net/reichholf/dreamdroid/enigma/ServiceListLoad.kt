package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2g: load typed service list via coroutines (no executor / runBlocking).
 * Uses a dedicated [SimpleHttpClient] per load (same as GetServiceListTask).
 * Success is "!http.hasError()" — empty lists are success (matches the former task).
 */
fun Fragment.launchServiceListLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, services: List<Service>, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val services = EnigmaClient(http).getServices(params)
        if (!isAdded) {
            return@launch
        }
        val success = !http.hasError()
        val errorText = if (success) {
            null
        } else {
            getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
        }
        onResult(success, services, errorText)
    }
}
