package net.reichholf.dreamdroid.ui.video

import android.content.Context
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.ui.epg.toEpgDetailContent
import net.reichholf.dreamdroid.ui.movies.toMovieDetailContent

/** Phase 2.1g-ii-d helpers so [VideoOverlayFragment] can open Compose modal sheets. */
fun VideoOverlayUiState.showMovieDetail(movie: Movie) {
    movieDetailContent = movie.toMovieDetailContent()
    epgDetailContent = null
}

fun VideoOverlayUiState.showEpgDetail(context: Context, event: Event) {
    val minutesShort = context.getString(R.string.minutes_short)
    epgDetailContent = event.toEpgDetailContent(minutesShort)
    movieDetailContent = null
}
