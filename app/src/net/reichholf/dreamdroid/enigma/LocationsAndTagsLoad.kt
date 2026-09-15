package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
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
 */
fun LifecycleOwner.launchLocationsAndTagsLoad(
    context: Context,
    onProgress: (title: String, progress: String) -> Unit,
    onReady: () -> Unit
): Job = lifecycleScope.launch {
    val http = SimpleHttpClient.getInstance()
    if (DreamDroid.getLocations().size == 0) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            return@launch
        }
        onProgress(
            context.getString(R.string.loading),
            context.getString(R.string.locations) + " - " +
                context.getString(R.string.fetching_data)
        )
        withContext(Dispatchers.IO) {
            DreamDroid.loadLocations(http)
        }
    }
    if (DreamDroid.getTags().size == 0) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            return@launch
        }
        onProgress(
            context.getString(R.string.loading),
            context.getString(R.string.tags) + " - " +
                context.getString(R.string.fetching_data)
        )
        withContext(Dispatchers.IO) {
            DreamDroid.loadTags(http)
        }
    }
    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
        return@launch
    }
    onReady()
}

fun Fragment.launchLocationsAndTagsLoad(
    onProgress: (title: String, progress: String) -> Unit,
    onReady: () -> Unit
): Job = launchLocationsAndTagsLoad(requireContext(), onProgress, onReady)
