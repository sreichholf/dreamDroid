package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient
import net.reichholf.dreamdroid.helpers.enigma2.Request
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import java.util.ArrayList

/**
 * Phase 2.2p: load screenshot bytes via coroutines (no AsyncByteLoader).
 * Call from a fragment that already has a view ([Fragment.getViewLifecycleOwner]).
 *
 * Dedicated [SimpleHttpClient] per load (same as the old loader).
 * Job.cancel does not abort in-flight HttpURLConnection I/O.
 */
fun Fragment.launchScreenshotLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, bytes: ByteArray?, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val bytes = withContext(Dispatchers.IO) {
            Request.getBytes(http, URIStore.SCREENSHOT, ArrayList(params))
        }
        if (!isAdded) {
            return@launch
        }
        val success = bytes.isNotEmpty()
        val errorText = when {
            success -> null
            http.hasError() -> http.getErrorText(requireContext())
            else -> getString(R.string.error)
        }
        onResult(success, if (success) bytes else null, errorText)
    }
}
