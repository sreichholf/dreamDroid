package net.reichholf.dreamdroid.enigma

import android.content.Context
import net.reichholf.dreamdroid.R
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
