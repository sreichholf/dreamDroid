package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.NameValuePair

data class MovieListLoadResult(
    val success: Boolean,
    val movies: List<Movie>,
    val errorText: String?
)

/**
 * Load typed movie list without a Fragment owner.
 * Null parse result is failure (not an empty list).
 * Caller is responsible for locations/tags prefetch when needed.
 */
suspend fun loadMovieList(context: Context, params: List<NameValuePair>): MovieListLoadResult {
    val response = EnigmaClient().getMovies(params)
    val success = response.value != null
    val movies = response.value ?: emptyList()
    val errorText = when {
        success -> null
        response.error != null -> response.error.contentError(context)
        else -> context.getString(R.string.error_parsing)
    }
    return MovieListLoadResult(success, movies, errorText)
}

fun Fragment.launchMovieListLoad(
    params: List<NameValuePair>,
    onResult: (success: Boolean, movies: List<Movie>, errorText: String?) -> Unit
): Job {
    return viewLifecycleOwner.lifecycleScope.launch {
        val http = EnigmaHttp()
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
        if (!isAdded) {
            return@launch
        }
        val result = loadMovieList(requireContext(), params)
        if (!isAdded) {
            return@launch
        }
        onResult(result.success, result.movies, result.errorText)
    }
}
