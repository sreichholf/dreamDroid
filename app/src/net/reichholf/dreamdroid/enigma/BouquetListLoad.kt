package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2i: load TV+Radio bouquet roots via coroutines (no executor / runBlocking).
 * Uses a dedicated [SimpleHttpClient] per load (same as GetBouquetListTask).
 * Success keeps partial roots: `hasAny || !http.hasError()` (matches the former task).
 */
fun Fragment.launchBouquetListLoad(
    onResult: (success: Boolean, bouquets: Bouquets, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val client = EnigmaClient(http)
        val bouquets = Bouquets()
        val tvRef = resources.getStringArray(R.array.servicerefstv)[0]
        val radioRef = resources.getStringArray(R.array.servicerefsradio)[0]
        bouquets.tv.addAll(client.getServices(listOf(NameValuePair("sRef", tvRef))))
        bouquets.radio.addAll(client.getServices(listOf(NameValuePair("sRef", radioRef))))
        if (!isAdded) {
            return@launch
        }
        val hadError = http.hasError()
        val hasAny = bouquets.tv.isNotEmpty() || bouquets.radio.isNotEmpty()
        val success = hasAny || !hadError
        val errorText = if (success) {
            null
        } else {
            getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
        }
        onResult(success, bouquets, errorText)
    }
}
