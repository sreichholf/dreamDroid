package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2j: prefetch locations/tags via coroutines (no executor).
 * Uses a dedicated [SimpleHttpClient] per load (same as GetLocationsAndTagsTask).
 * Always calls [onReady] when finished (matches the former task, which ignored load failures).
 * Uses fragment [lifecycleScope] so TimerEdit can start from onCreateView.
 */
fun Fragment.launchLocationsAndTagsLoad(
    onProgress: (title: String, progress: String) -> Unit,
    onReady: () -> Unit,
): Job {
    return lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        if (DreamDroid.getLocations().size == 0) {
            if (!isAdded) {
                return@launch
            }
            onProgress(
                getString(R.string.loading),
                getString(R.string.locations) + " - " + getString(R.string.fetching_data),
            )
            withContext(Dispatchers.IO) {
                DreamDroid.loadLocations(http)
            }
        }
        if (DreamDroid.getTags().size == 0) {
            if (!isAdded) {
                return@launch
            }
            onProgress(
                getString(R.string.loading),
                getString(R.string.tags) + " - " + getString(R.string.fetching_data),
            )
            withContext(Dispatchers.IO) {
                DreamDroid.loadTags(http)
            }
        }
        if (!isAdded) {
            return@launch
        }
        onReady()
    }
}
