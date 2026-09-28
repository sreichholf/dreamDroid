package net.reichholf.dreamdroid.ui.current

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CurrentServiceSavedTest {
    @Test
    fun absentSnapshotIsNotRestored() {
        val saved = readCurrentServiceSaved(SavedStateHandle(), currentProfileId = 3)
        assertNull(saved.current)
        assertNull(saved.profileId)
        assertFalse(shouldRestoreCurrentService(null, 3))
    }

    @Test
    fun currentServiceRoundTripsWithProfileId() {
        val current = CurrentService(
            service = Service(reference = "1:0:1:6DCA", name = "Das Erste HD")
        )
        val handle = SavedStateHandle()
        CurrentServiceSaved(current = current, profileId = 4).writeTo(handle)
        val saved = readCurrentServiceSaved(handle, currentProfileId = 4)
        assertEquals(current, saved.current)
        assertEquals(4, saved.profileId)
        assertEquals(current, handle.get<Any>(CurrentServiceSavedKeys.CURRENT))
        assertEquals(4, handle.get<Any>(CurrentServiceSavedKeys.PROFILE_ID))
        assertTrue(shouldRestoreCurrentService(saved.profileId, 4))
    }

    @Test
    fun differentProfileIdIgnoresSnapshot() {
        val current = CurrentService(service = Service(reference = "1:0:1:1", name = "Box A"))
        val handle = SavedStateHandle()
        CurrentServiceSaved(current = current, profileId = 4).writeTo(handle)
        val saved = readCurrentServiceSaved(handle, currentProfileId = 9)
        assertNull(saved.current)
        assertNull(saved.profileId)
        assertFalse(shouldRestoreCurrentService(4, 9))
    }
}
