package net.reichholf.dreamdroid.ui.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HubShellSavedTest {
    @Test
    fun absentSnapshotReadsDefaultsWithoutWriting() {
        val values = mutableMapOf<String, Any>()
        val access = MapHubShellSavedAccess(values)
        val saved = readHubShellSaved(access)
        assertEquals(HubModes.TV, saved.mode)
        assertNull(saved.currentTv)
        assertNull(saved.currentRadio)
        assertNull(saved.currentMovie)
        assertEquals(0, saved.selectedRow)
        assertEquals(0, saved.timerRemountEpoch)
        assertEquals(0, saved.nowPlayingReloadEpoch)
        assertFalse(values.containsKey(HubShellSavedKeys.MODE))
        assertFalse(values.containsKey(HubShellSavedKeys.SELECTED_ROW))
    }

    @Test
    fun roundTripKeepsModeRowAndBouquetRefs() {
        val values = mutableMapOf<String, Any>()
        val access = MapHubShellSavedAccess(values)
        HubShellSaved(
            mode = HubModes.RADIO,
            currentTv = "1:7:1:tv",
            currentRadio = "1:7:2:radio",
            currentMovie = "/hdd/movie",
            selectedRow = 2,
            timerRemountEpoch = 4,
            nowPlayingReloadEpoch = 5
        ).writeTo(access)
        val saved = readHubShellSaved(access)
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
        val values = mutableMapOf<String, Any>()
        val access = MapHubShellSavedAccess(values)
        HubShellSaved(currentMovie = "/hdd/movie").writeTo(access)
        HubShellSaved(currentMovie = null).writeTo(access)
        assertFalse(values.containsKey(HubShellSavedKeys.CURRENT_MOVIE))
        assertNull(readHubShellSaved(access).currentMovie)
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
