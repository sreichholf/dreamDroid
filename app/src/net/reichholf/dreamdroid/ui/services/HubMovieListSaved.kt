package net.reichholf.dreamdroid.ui.services

import java.util.ArrayList

fun hubMovieSelectedTagsKey(location: String): String = "hub_movie_selected_tags:$location"

interface HubMovieListSavedAccess {
    fun getSelectedTags(location: String): List<String>?

    fun setSelectedTags(location: String, tags: List<String>)
}

class MapHubMovieListSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    HubMovieListSavedAccess {
    override fun getSelectedTags(location: String): List<String>? {
        val stored = values[hubMovieSelectedTagsKey(location)] ?: return null
        @Suppress("UNCHECKED_CAST")
        return (stored as? List<String>)?.toList()
    }

    override fun setSelectedTags(location: String, tags: List<String>) {
        values[hubMovieSelectedTagsKey(location)] = ArrayList(tags)
    }
}

/** Absent tags read as empty. Reading does not write. */
fun readHubMovieSelectedTags(access: HubMovieListSavedAccess, location: String): List<String> =
    access.getSelectedTags(location).orEmpty()

fun writeHubMovieSelectedTags(
    access: HubMovieListSavedAccess,
    location: String,
    tags: List<String>
) {
    access.setSelectedTags(location, tags)
}
