package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

data class ServiceListLoadResult(
    val success: Boolean,
    val services: List<Service>,
    val errorText: String?,
)

/**
 * Phase 2.7d: load typed service list without a Fragment owner.
 * Success is "!http.hasError()" — empty lists are success (matches the former task).
 */
suspend fun loadServiceList(
    context: Context,
    params: List<NameValuePair>,
): ServiceListLoadResult {
    val http = SimpleHttpClient.getInstance()
    val services = EnigmaClient(http).getServices(params)
    val success = !http.hasError()
    val errorText = if (success) {
        null
    } else {
        context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
    }
    return ServiceListLoadResult(success, services, errorText)
}

/**
 * Phase 2.2g: load typed service list via coroutines (no executor / runBlocking).
 * Uses a dedicated [SimpleHttpClient] per load (same as GetServiceListTask).
 */
fun Fragment.launchServiceListLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, services: List<Service>, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val result = loadServiceList(requireContext(), params)
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.services, result.errorText)
    }
}
