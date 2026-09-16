package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R

data class TimerListLoadResult(
    val success: Boolean,
    val timers: List<Timer>,
    val errorText: String?
)

/**
 * Load typed timer list without a Fragment owner.
 * Null parse result is failure (not an empty list) — matches the former task.
 */
suspend fun loadTimerList(context: Context): TimerListLoadResult {
    val response = EnigmaClient().getTimers()
    val success = response.value != null
    val timers = response.value ?: emptyList()
    val errorText = when {
        success -> null
        response.error != null -> response.error.contentError(context)
        else -> context.getString(R.string.error_parsing)
    }
    return TimerListLoadResult(success, timers, errorText)
}

fun Fragment.launchTimerListLoad(
    onResult: (success: Boolean, timers: List<Timer>, errorText: String?) -> Unit
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val result = loadTimerList(requireContext())
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.timers, result.errorText)
    }
}
