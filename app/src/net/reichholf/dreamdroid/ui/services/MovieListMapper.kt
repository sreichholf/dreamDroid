package net.reichholf.dreamdroid.ui.services

import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Movie

fun movieListItemsFrom(maps: List<ExtendedHashMap>): List<MovieListItem> {
    return maps.mapIndexed { index, map ->
        MovieListItem(
            index = index,
            title = map.getString(Movie.KEY_TITLE).orEmpty(),
            serviceName = map.getString(Movie.KEY_SERVICE_NAME).orEmpty(),
            fileSize = map.getString(Movie.KEY_FILE_SIZE_READABLE).orEmpty(),
            time = map.getString(Movie.KEY_TIME_READABLE).orEmpty(),
            length = map.getString(Movie.KEY_LENGTH).orEmpty(),
        )
    }
}
