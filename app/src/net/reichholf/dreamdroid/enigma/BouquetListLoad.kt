package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

data class BouquetListLoadResult(
    val success: Boolean,
    val bouquets: Bouquets,
    val errorText: String?,
)

/**
 * Phase 2.7f: load TV+Radio bouquet roots without a Fragment owner.
 * Success keeps partial roots: `hasAny || !http.hasError()`.
 */
suspend fun loadBouquetList(context: Context): BouquetListLoadResult {
    val http = SimpleHttpClient.getInstance()
    val client = EnigmaClient(http)
    val bouquets = Bouquets()
    val tvRef = context.resources.getStringArray(R.array.servicerefstv)[0]
    val radioRef = context.resources.getStringArray(R.array.servicerefsradio)[0]
    bouquets.tv.addAll(client.getServices(listOf(NameValuePair("sRef", tvRef))))
    bouquets.radio.addAll(client.getServices(listOf(NameValuePair("sRef", radioRef))))
    val hadError = http.hasError()
    val hasAny = bouquets.tv.isNotEmpty() || bouquets.radio.isNotEmpty()
    val success = hasAny || !hadError
    val errorText = if (success) {
        null
    } else {
        context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
    }
    return BouquetListLoadResult(success, bouquets, errorText)
}

/**
 * Phase 2.2i: load TV+Radio bouquet roots via coroutines (no executor / runBlocking).
 */
fun Fragment.launchBouquetListLoad(
    onResult: (success: Boolean, bouquets: Bouquets, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val result = loadBouquetList(requireContext())
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.bouquets, result.errorText)
    }
}
