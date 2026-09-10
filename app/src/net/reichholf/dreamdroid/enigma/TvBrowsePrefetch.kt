package net.reichholf.dreamdroid.enigma

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2r: prefetch locations/tags for Leanback RootBrowse.
 * Matches AsyncListLoader / MovieListLoad: refresh when cache size <= 1
 * (covers the single-entry failed-fetch fallback).
 */
fun Fragment.launchTvBrowsePrefetch(onReady: () -> Unit): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        withContext(Dispatchers.IO) {
            if (DreamDroid.getLocations().size <= 1) {
                if (!DreamDroid.loadLocations(http)) {
                    Log.e(DreamDroid.LOG_TAG, "ERROR loading locations")
                }
            }
            if (DreamDroid.getTags().size <= 1) {
                if (!DreamDroid.loadTags(http)) {
                    Log.e(DreamDroid.LOG_TAG, "ERROR loading tags")
                }
            }
        }
        if (!isAdded) {
            return@launch
        }
        onReady()
    }
}
