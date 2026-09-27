package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
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
): Job = lifecycleScope.launchLocationsAndTagsLoad(context, onProgress, onReady, onLocationsResult)

fun CoroutineScope.launchLocationsAndTagsLoad(
    context: Context,
    onProgress: (title: String, progress: String) -> Unit,
    onReady: () -> Unit,
    onLocationsResult: ((success: Boolean) -> Unit)? = null
): Job = launch {
    val http = EnigmaHttp()
    var locationsOk = ProfileRepository.get().locationsLoadedFromReceiver()
    if (ProfileRepository.get().locations().size == 0) {
        onProgress(
            context.getString(R.string.loading),
            context.getString(R.string.locations) + " - " +
                context.getString(R.string.fetching_data)
        )
        locationsOk = withContext(Dispatchers.IO) {
            ProfileRepository.get().loadLocations(http)
        }
    }
    if (ProfileRepository.get().tags().size == 0) {
        onProgress(
            context.getString(R.string.loading),
            context.getString(R.string.tags) + " - " +
                context.getString(R.string.fetching_data)
        )
        withContext(Dispatchers.IO) {
            ProfileRepository.get().loadTags(http)
        }
    }
    onLocationsResult?.invoke(locationsOk)
    onReady()
}
