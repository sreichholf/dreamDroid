package net.reichholf.dreamdroid.ui.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HubMovieListSavedTest {
    @Test
    fun absentTagsReadEmptyWithoutWriting() {
        val values = mutableMapOf<String, Any>()
        val access = MapHubMovieListSavedAccess(values)
        assertEquals(emptyList<String>(), readHubMovieSelectedTags(access, "/hdd/movie"))
        assertFalse(values.containsKey(hubMovieSelectedTagsKey("/hdd/movie")))
    }

    @Test
    fun tagsAreNamespacedPerLocation() {
        val values = mutableMapOf<String, Any>()
        val access = MapHubMovieListSavedAccess(values)
        writeHubMovieSelectedTags(access, "/hdd/movie", listOf("news"))
        writeHubMovieSelectedTags(access, "/media/hdd", listOf("sport"))
        assertEquals(listOf("news"), readHubMovieSelectedTags(access, "/hdd/movie"))
        assertEquals(listOf("sport"), readHubMovieSelectedTags(access, "/media/hdd"))
        assertTrue(
            hubMovieSelectedTagsKey("/hdd/movie") != hubMovieSelectedTagsKey("/media/hdd")
        )
    }
}
