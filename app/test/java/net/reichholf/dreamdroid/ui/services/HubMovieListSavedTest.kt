package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HubMovieListSavedTest {
    @Test
    fun absentTagsReadEmptyWithoutWriting() {
        val handle = SavedStateHandle()
        assertEquals(emptyList<String>(), readHubMovieSelectedTags(handle, "/hdd/movie"))
        assertFalse(handle.contains(hubMovieSelectedTagsKey("/hdd/movie")))
    }

    @Test
    fun tagsAreNamespacedPerLocation() {
        val handle = SavedStateHandle()
        writeHubMovieSelectedTags(handle, "/hdd/movie", listOf("news"))
        writeHubMovieSelectedTags(handle, "/media/hdd", listOf("sport"))
        assertEquals(listOf("news"), readHubMovieSelectedTags(handle, "/hdd/movie"))
        assertEquals(listOf("sport"), readHubMovieSelectedTags(handle, "/media/hdd"))
        assertTrue(
            hubMovieSelectedTagsKey("/hdd/movie") != hubMovieSelectedTagsKey("/media/hdd")
        )
    }
}
