package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2c: load typed current service via coroutines (no executor / runBlocking).
 * Call from a fragment that already has a view ([Fragment.getViewLifecycleOwner]).
 *
 * Uses a dedicated [SimpleHttpClient] per load (same as the old GetCurrentServiceTask).
 */
fun Fragment.launchCurrentServiceLoad(
    onResult: (success: Boolean, current: CurrentService?, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val current = EnigmaClient(http).getCurrent()
        if (!isAdded) {
            return@launch
        }
        // Match GetCurrentServiceTask: non-null parse result counts as success;
        // empty payload is handled by the fragment UI (keep last-good / toast).
        val success = current != null
        val errorText = when {
            success -> null
            http.hasError() ->
                getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
            else -> getString(R.string.error_parsing)
        }
        onResult(success, current, errorText)
    }
}
