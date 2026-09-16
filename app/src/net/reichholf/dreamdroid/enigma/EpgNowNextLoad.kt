package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

data class EpgNowNextLoadResult(
    val success: Boolean,
    val rows: List<ServiceNowNext>,
    val errorText: String?
)

/**
 * Load typed hub now/next rows without a Fragment owner.
 * Null fetch is failure. Empty 200 stays an empty list.
 */
suspend fun loadEpgNowNext(context: Context, params: List<NameValuePair>): EpgNowNextLoadResult {
    val uri = if (DreamDroid.featureNowNext()) URIStore.EPG_NOWNEXT else URIStore.EPG_NOW
    val response = EnigmaClient().getEpgNowNext(params, uri)
    val success = response.value != null
    val rows = response.value ?: emptyList()
    val errorText = if (success) null else response.error.contentError(context)
    return EpgNowNextLoadResult(success, rows, errorText)
}

fun Fragment.launchEpgNowNextLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, rows: List<ServiceNowNext>, errorText: String?) -> Unit
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val result = loadEpgNowNext(requireContext(), params)
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.rows, result.errorText)
    }
}
