package net.reichholf.dreamdroid.enigma

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.SimpleHttpClient

/**
 * Phase 2.2e: load typed movie list via coroutines (no executor / runBlocking).
 * Prefetches locations/tags on a dedicated [SimpleHttpClient] like GetMovieListTask.
 * Null parse result is failure (not an empty list).
 */
fun Fragment.launchMovieListLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, movies: List<Movie>, errorText: String?) -> Unit,
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = SimpleHttpClient.getInstance()
        withContext(Dispatchers.IO) {
            if (DreamDroid.getLocations().size <= 1) {
                if (!DreamDroid.loadLocations(http)) {
                    android.util.Log.e(DreamDroid.LOG_TAG, "ERROR loading locations")
                }
            }
            if (DreamDroid.getTags().size <= 1) {
                if (!DreamDroid.loadTags(http)) {
                    android.util.Log.e(DreamDroid.LOG_TAG, "ERROR loading tags")
                }
            }
        }
        val fetched = EnigmaClient(http).getMovies(params)
        if (!isAdded) {
            return@launch
        }
        val success = fetched != null
        val movies = fetched ?: emptyList()
        val errorText = when {
            success -> null
            http.hasError() ->
                getString(R.string.get_content_error) + "\n" + http.getErrorText(requireContext())
            else -> getString(R.string.error_parsing)
        }
        onResult(success, movies, errorText)
    }
}
