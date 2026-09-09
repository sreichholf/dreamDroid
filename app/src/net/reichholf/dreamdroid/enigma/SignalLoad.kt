package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2b: load typed signal via coroutines (no executor / runBlocking).
 * Call from a fragment that already has a view ([Fragment.getViewLifecycleOwner]).
 *
 * Uses a dedicated [SimpleHttpClient] per load (same as the old GetSignalTask),
 * not the fragment helper’s shared client — [SimpleHttpClient] is not thread-safe and
 * Job.cancel does not abort in-flight HttpURLConnection I/O.
 */
fun Fragment.launchSignalLoad(
    onResult: (success: Boolean, signal: Signal?, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val signal = EnigmaClient(http).getSignal()
        if (!isAdded) {
            return@launch
        }
        val success = signal != null && !signal.isEmpty()
        val errorText = when {
            success -> null
            http.hasError() ->
                getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
            else -> getString(R.string.error_parsing)
        }
        onResult(success, signal, errorText)
    }
}
