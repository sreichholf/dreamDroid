package net.reichholf.dreamdroid.ui.current

import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CurrentServiceSavedTest {
    @Test
    fun absentSnapshotIsNotRestored() {
        val saved = readCurrentServiceSaved(
            MapCurrentServiceSavedAccess(),
            currentProfileId = 3
        )
        assertNull(saved.current)
        assertNull(saved.item)
        assertFalse(saved.ready)
        assertNull(saved.profileId)
        assertFalse(shouldRestoreCurrentService(null, 3))
    }

    @Test
    fun currentServiceRoundTripsWithReadyAndProfileId() {
        val current = CurrentService(
            service = Service(reference = "1:0:1:6DCA", name = "Das Erste HD")
        )
        val item = Event()
        val values = mutableMapOf<String, Any>()
        val access = MapCurrentServiceSavedAccess(values)
        CurrentServiceSaved(
            current = current,
            item = item,
            ready = true,
            profileId = 4
        ).writeTo(access)
        val saved = readCurrentServiceSaved(access, currentProfileId = 4)
        assertEquals("Das Erste HD", saved.current?.service?.name)
        assertEquals(current, saved.current)
        assertEquals(item, saved.item)
        assertTrue(saved.ready)
        assertEquals(4, saved.profileId)
        assertEquals(current, values[CurrentServiceSavedKeys.CURRENT])
        assertEquals(true, values[CurrentServiceSavedKeys.READY])
        assertEquals(4, values[CurrentServiceSavedKeys.PROFILE_ID])
        assertTrue(shouldRestoreCurrentService(saved.profileId, 4))
    }

    @Test
    fun differentProfileIdIgnoresSnapshot() {
        val current = CurrentService(
            service = Service(reference = "1:0:1:1", name = "Box A")
        )
        val access = MapCurrentServiceSavedAccess()
        CurrentServiceSaved(
            current = current,
            item = Event(),
            ready = true,
            profileId = 4
        ).writeTo(access)
        val saved = readCurrentServiceSaved(access, currentProfileId = 9)
        assertNull(saved.current)
        assertNull(saved.item)
        assertFalse(saved.ready)
        assertNull(saved.profileId)
        assertFalse(shouldRestoreCurrentService(4, 9))
    }
}
