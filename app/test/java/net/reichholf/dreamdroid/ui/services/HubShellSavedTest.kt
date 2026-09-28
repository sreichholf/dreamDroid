package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HubShellSavedTest {
    @Test
    fun absentSnapshotReadsDefaultsWithoutWriting() {
        val handle = SavedStateHandle()
        val saved = readHubShellSaved(handle)
        assertEquals(HubModes.TV, saved.mode)
        assertNull(saved.currentTv)
        assertNull(saved.currentRadio)
        assertNull(saved.currentMovie)
        assertEquals(0, saved.selectedRow)
        assertEquals(0, saved.timerRemountEpoch)
        assertEquals(0, saved.nowPlayingReloadEpoch)
        assertFalse(handle.contains(HubShellSavedKeys.MODE))
        assertFalse(handle.contains(HubShellSavedKeys.SELECTED_ROW))
    }

    @Test
    fun roundTripKeepsModeRowAndBouquetRefs() {
        val handle = SavedStateHandle()
        HubShellSaved(
            mode = HubModes.RADIO,
            currentTv = "1:7:1:tv",
            currentRadio = "1:7:2:radio",
            currentMovie = "/hdd/movie",
            selectedRow = 2,
            timerRemountEpoch = 4,
            nowPlayingReloadEpoch = 5
        ).writeTo(handle)
        val saved = readHubShellSaved(handle)
        assertEquals(HubModes.RADIO, saved.mode)
        assertEquals("1:7:1:tv", saved.currentTv)
        assertEquals("1:7:2:radio", saved.currentRadio)
        assertEquals("/hdd/movie", saved.currentMovie)
        assertEquals(2, saved.selectedRow)
        assertEquals(4, saved.timerRemountEpoch)
        assertEquals(5, saved.nowPlayingReloadEpoch)
    }

    @Test
    fun nullMovieRefRemovesTheKey() {
        val handle = SavedStateHandle()
        HubShellSaved(currentMovie = "/hdd/movie").writeTo(handle)
        HubShellSaved(currentMovie = null).writeTo(handle)
        assertFalse(handle.contains(HubShellSavedKeys.CURRENT_MOVIE))
        assertNull(readHubShellSaved(handle).currentMovie)
    }

    @Test
    fun sameLoadKeySkipsAnotherHubPageLoad() {
        assertTrue(shouldLoadHubPage(appliedKey = null, nextKey = "Online"))
        assertFalse(shouldLoadHubPage(appliedKey = "Online", nextKey = "Online"))
        assertTrue(shouldLoadHubPage(appliedKey = "0", nextKey = "1"))
    }

    @Test
    fun emptyFailedLocationsLoadRetries() {
        assertTrue(
            shouldRetryHubLocations(
                ready = false,
                locations = emptyList(),
                jobActive = false,
                lastHttpSuccess = null
            )
        )
        assertFalse(
            shouldRetryHubLocations(
                ready = true,
                locations = listOf("/hdd/movie"),
                jobActive = false,
                lastHttpSuccess = false
            )
        )
        assertTrue(
            shouldRetryHubLocations(
                ready = true,
                locations = emptyList(),
                jobActive = false,
                lastHttpSuccess = false
            )
        )
        assertFalse(
            shouldRetryHubLocations(
                ready = true,
                locations = emptyList(),
                jobActive = false,
                lastHttpSuccess = true
            )
        )
        assertFalse(
            shouldRetryHubLocations(
                ready = false,
                locations = emptyList(),
                jobActive = true,
                lastHttpSuccess = null
            )
        )
    }
}
