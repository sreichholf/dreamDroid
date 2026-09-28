package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle

fun hubMovieSelectedTagsKey(location: String): String = "hub_movie_selected_tags:$location"

/** Absent tags read as empty. Reading does not write. */
fun readHubMovieSelectedTags(handle: SavedStateHandle, location: String): List<String> =
    handle.get<ArrayList<String>>(hubMovieSelectedTagsKey(location))?.toList().orEmpty()

fun writeHubMovieSelectedTags(handle: SavedStateHandle, location: String, tags: List<String>) {
    handle[hubMovieSelectedTagsKey(location)] = ArrayList(tags)
}
