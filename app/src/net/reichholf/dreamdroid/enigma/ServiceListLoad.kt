package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.helpers.NameValuePair

data class ServiceListLoadResult(
    val success: Boolean,
    val services: List<Service>,
    val errorText: String?
)

/**
 * Load typed service list without a Fragment owner.
 * Null fetch is failure. Empty 200 stays an empty list.
 */
suspend fun loadServiceList(context: Context, params: List<NameValuePair>): ServiceListLoadResult {
    val response = EnigmaClient().getServices(params)
    val success = response.value != null
    val services = response.value ?: emptyList()
    val errorText = if (success) null else response.error.contentError(context)
    return ServiceListLoadResult(success, services, errorText)
}

fun Fragment.launchServiceListLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, services: List<Service>, errorText: String?) -> Unit
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val result = loadServiceList(requireContext(), params)
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.services, result.errorText)
    }
}
