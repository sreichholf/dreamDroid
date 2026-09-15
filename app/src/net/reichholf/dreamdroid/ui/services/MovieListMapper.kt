package net.reichholf.dreamdroid.ui.services

import net.reichholf.dreamdroid.enigma.Movie

fun movieListItemsFromMovies(movies: List<Movie>): List<MovieListItem> = movies.mapIndexed {
        index,
        movie
    ->
    MovieListItem(
        index = index,
        title = movie.title,
        serviceName = movie.serviceName,
        fileSize = movie.fileSizeReadable,
        time = movie.timeReadable,
        length = movie.length
    )
}
