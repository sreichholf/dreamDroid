package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2d: load typed timer list via coroutines (no executor / runBlocking).
 * Call from a fragment that already has a view ([Fragment.getViewLifecycleOwner]).
 *
 * Uses a dedicated [SimpleHttpClient] per load (same as the old GetTimerListTask).
 * Null parse result is failure (not an empty list) — matches the former task.
 */
fun Fragment.launchTimerListLoad(
    onResult: (success: Boolean, timers: List<Timer>, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        val fetched = EnigmaClient(http).getTimers()
        if (!isAdded) {
            return@launch
        }
        val success = fetched != null
        val timers = fetched ?: emptyList()
        val errorText = when {
            success -> null
            http.hasError() ->
                getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
            else -> getString(R.string.error_parsing)
        }
        onResult(success, timers, errorText)
    }
}
