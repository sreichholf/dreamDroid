package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttp

/**
 * Phase 2.2j: prefetch locations/tags via coroutines (no executor).
 * Prefetch locations/tags via coroutines.
 * Always calls [onReady] when finished (matches the former task, which ignored load failures).
 * [onLocationsResult] reports whether locations came from a successful receiver parse
 * (not the `/hdd/movie` fallback).
 */
fun LifecycleOwner.launchLocationsAndTagsLoad(
    context: Context,
    onProgress: (title: String, progress: String) -> Unit,
    onReady: () -> Unit,
    onLocationsResult: ((success: Boolean) -> Unit)? = null
): Job = lifecycleScope.launch {
    val http = EnigmaHttp()
    var locationsOk = DreamDroid.locationsLoadedFromReceiver()
    if (DreamDroid.getLocations().size == 0) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            return@launch
        }
        onProgress(
            context.getString(R.string.loading),
            context.getString(R.string.locations) + " - " +
                context.getString(R.string.fetching_data)
        )
        locationsOk = withContext(Dispatchers.IO) {
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
    onLocationsResult?.invoke(locationsOk)
    onReady()
}
