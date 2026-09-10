package net.reichholf.dreamdroid.ui.services

import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Movie as MovieKeys

fun movieListItemsFrom(maps: List<ExtendedHashMap>): List<MovieListItem> {
    return maps.mapIndexed { index, map ->
        MovieListItem(
            index = index,
            title = map.getString(MovieKeys.KEY_TITLE).orEmpty(),
            serviceName = map.getString(MovieKeys.KEY_SERVICE_NAME).orEmpty(),
            fileSize = map.getString(MovieKeys.KEY_FILE_SIZE_READABLE).orEmpty(),
            time = map.getString(MovieKeys.KEY_TIME_READABLE).orEmpty(),
            length = map.getString(MovieKeys.KEY_LENGTH).orEmpty(),
        )
    }
}

fun movieListItemsFromMovies(movies: List<Movie>): List<MovieListItem> {
    return movies.mapIndexed { index, movie ->
        MovieListItem(
            index = index,
            title = movie.title,
            serviceName = movie.serviceName,
            fileSize = movie.fileSizeReadable,
            time = movie.timeReadable,
            length = movie.length,
        )
    }
}

fun movieToExtendedHashMap(movie: Movie): ExtendedHashMap {
    val map = ExtendedHashMap()
    map.put(MovieKeys.KEY_REFERENCE, movie.reference)
    map.put(MovieKeys.KEY_TITLE, movie.title)
    map.put(MovieKeys.KEY_DESCRIPTION, movie.description)
    map.put(MovieKeys.KEY_DESCRIPTION_EXTENDED, movie.descriptionExtended)
    map.put(MovieKeys.KEY_SERVICE_NAME, movie.serviceName)
    map.put(MovieKeys.KEY_TIME, movie.time)
    map.put(MovieKeys.KEY_TIME_READABLE, movie.timeReadable)
    map.put(MovieKeys.KEY_LENGTH, movie.length)
    map.put(MovieKeys.KEY_TAGS, movie.tags)
    map.put(MovieKeys.KEY_FILE_NAME, movie.fileName)
    map.put(MovieKeys.KEY_FILE_SIZE, movie.fileSize)
    map.put(MovieKeys.KEY_FILE_SIZE_READABLE, movie.fileSizeReadable)
    return map
}

/** Inverse of [movieToExtendedHashMap] for Intent / legacy hash edges. */
fun movieFromExtendedHashMap(map: ExtendedHashMap): Movie {
    return Movie(
        reference = map.getString(MovieKeys.KEY_REFERENCE).orEmpty(),
        title = map.getString(MovieKeys.KEY_TITLE).orEmpty(),
        description = map.getString(MovieKeys.KEY_DESCRIPTION).orEmpty(),
        descriptionExtended = map.getString(MovieKeys.KEY_DESCRIPTION_EXTENDED).orEmpty(),
        serviceName = map.getString(MovieKeys.KEY_SERVICE_NAME).orEmpty(),
        time = map.getString(MovieKeys.KEY_TIME).orEmpty(),
        timeReadable = map.getString(MovieKeys.KEY_TIME_READABLE).orEmpty(),
        length = map.getString(MovieKeys.KEY_LENGTH).orEmpty(),
        tags = map.getString(MovieKeys.KEY_TAGS).orEmpty(),
        fileName = map.getString(MovieKeys.KEY_FILE_NAME).orEmpty(),
        fileSize = map.getString(MovieKeys.KEY_FILE_SIZE).orEmpty(),
        fileSizeReadable = map.getString(MovieKeys.KEY_FILE_SIZE_READABLE).orEmpty(),
    )
}
