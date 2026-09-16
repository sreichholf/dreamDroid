package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair

data class BouquetListLoadResult(
    val success: Boolean,
    val bouquets: Bouquets,
    val errorText: String?
)

/**
 * Load TV+Radio bouquet roots without a Fragment owner.
 * TV-root HTTP fail is failure even if radio later succeeds.
 */
suspend fun loadBouquetList(context: Context): BouquetListLoadResult {
    val client = EnigmaClient()
    val bouquets = Bouquets()
    val tvRef = context.resources.getStringArray(R.array.servicerefstv)[0]
    val radioRef = context.resources.getStringArray(R.array.servicerefsradio)[0]
    val tv = client.getServices(listOf(NameValuePair("sRef", tvRef)))
    val tvList = tv.value
    if (tvList == null) {
        return BouquetListLoadResult(false, bouquets, tv.error.contentError(context))
    }
    bouquets.tv.addAll(tvList)
    val radio = client.getServices(listOf(NameValuePair("sRef", radioRef)))
    val radioList = radio.value
    if (radioList != null) {
        bouquets.radio.addAll(radioList)
    }
    val success = radioList != null || tvList.isNotEmpty()
    val errorText = if (success) null else radio.error.contentError(context)
    return BouquetListLoadResult(success, bouquets, errorText)
}

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
