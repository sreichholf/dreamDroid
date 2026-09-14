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
    val errorText: String?
)

/**
 * Phase 2.7f: load TV+Radio bouquet roots without a Fragment owner.
 * TV-root HTTP fail is failure even if radio later succeeds.
 */
suspend fun loadBouquetList(context: Context): BouquetListLoadResult {
    val http = SimpleHttpClient.getInstance()
    val client = EnigmaClient(http)
    val bouquets = Bouquets()
    val tvRef = context.resources.getStringArray(R.array.servicerefstv)[0]
    val radioRef = context.resources.getStringArray(R.array.servicerefsradio)[0]
    val tv = client.getServices(listOf(NameValuePair("sRef", tvRef)))
    if (tv == null) {
        return BouquetListLoadResult(
            false,
            bouquets,
            context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
        )
    }
    bouquets.tv.addAll(tv)
    val radio = client.getServices(listOf(NameValuePair("sRef", radioRef)))
    if (radio != null) {
        bouquets.radio.addAll(radio)
    }
    val success = radio != null || tv.isNotEmpty()
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
    onResult: (success: Boolean, bouquets: Bouquets, errorText: String?) -> Unit
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val result = loadBouquetList(requireContext())
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.bouquets, result.errorText)
    }
}
