package net.reichholf.dreamdroid.ui.video

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContentOrUnavailable
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent

/** Opens the overlay's detail sheet on [movie]. */
fun VideoOverlayUiState.showMovieDetail(movie: Movie) {
    movieDetailContent = movie.toMovieDetailContent()
    epgDetailContent = null
}

/** Opens the overlay's detail sheet on [event]. */
fun VideoOverlayUiState.showEpgDetail(context: Context, event: Event) {
    val minutesShort = context.getString(R.string.minutes_short)
    val unavailable = context.getString(R.string.not_available)
    epgDetailContent = event.toEpgDetailContentOrUnavailable(minutesShort, unavailable)
    movieDetailContent = null
}
