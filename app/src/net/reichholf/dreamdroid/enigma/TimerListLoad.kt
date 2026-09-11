package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

data class TimerListLoadResult(
    val success: Boolean,
    val timers: List<Timer>,
    val errorText: String?,
)

/**
 * Phase 2.7h: load typed timer list without a Fragment owner.
 * Null parse result is failure (not an empty list) — matches the former task.
 */
suspend fun loadTimerList(context: Context): TimerListLoadResult {
    val http = SimpleHttpClient.getInstance()
    val fetched = EnigmaClient(http).getTimers()
    val success = fetched != null
    val timers = fetched ?: emptyList()
    val errorText = when {
        success -> null
        http.hasError() ->
            context.getString(R.string.get_content_error) + "\n" + http.getErrorText(context)
        else -> context.getString(R.string.error_parsing)
    }
    return TimerListLoadResult(success, timers, errorText)
}

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
        val result = loadTimerList(requireContext())
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.timers, result.errorText)
    }
}
