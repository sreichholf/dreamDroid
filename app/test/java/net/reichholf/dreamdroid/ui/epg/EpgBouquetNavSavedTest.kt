package net.reichholf.dreamdroid.ui.epg

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpgBouquetNavSavedTest {
    @Test
    fun savedKeys() {
        assertEquals("epg_bouquet_ref", EpgBouquetNavSavedKeys.BOUQUET_REF)
        assertEquals("epg_bouquet_name", EpgBouquetNavSavedKeys.BOUQUET_NAME)
        assertEquals("epg_bouquet_time_sec", EpgBouquetNavSavedKeys.TIME_SEC)
        assertEquals("epg_bouquet_waiting_for_picker", EpgBouquetNavSavedKeys.WAITING_FOR_PICKER)
    }

    @Test
    fun absentSnapshotReadsDefaultsWithoutStoring() {
        val handle = SavedStateHandle()
        val saved = readEpgBouquetNavSaved(handle)
        assertEquals("", saved.bouquetRef)
        assertEquals("", saved.bouquetName)
        assertNull(saved.timeSec)
        assertFalse(saved.waitingForPicker)
        assertTrue(handle.keys().isEmpty())
        assertFalse(handle.contains(EpgBouquetNavSavedKeys.BOUQUET_REF))
        assertFalse(handle.contains(EpgBouquetNavSavedKeys.BOUQUET_NAME))
        assertFalse(handle.contains(EpgBouquetNavSavedKeys.TIME_SEC))
        assertFalse(handle.contains(EpgBouquetNavSavedKeys.WAITING_FOR_PICKER))
    }

    @Test
    fun roundTripKeepsTimeSecZero() {
        val handle = SavedStateHandle()
        EpgBouquetNavSaved(
            bouquetRef = "1:7:1:B",
            bouquetName = "Favourites",
            timeSec = 0L,
            waitingForPicker = true
        ).writeTo(handle)
        val saved = readEpgBouquetNavSaved(handle)
        assertEquals("1:7:1:B", saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
        assertEquals(0L, saved.timeSec)
        assertTrue(saved.waitingForPicker)
        assertEquals("1:7:1:B", handle.get<Any>(EpgBouquetNavSavedKeys.BOUQUET_REF))
        assertEquals("Favourites", handle.get<Any>(EpgBouquetNavSavedKeys.BOUQUET_NAME))
        assertTrue(handle.contains(EpgBouquetNavSavedKeys.TIME_SEC))
        assertEquals(0L, handle.get<Any>(EpgBouquetNavSavedKeys.TIME_SEC))
        assertEquals(true, handle.get<Any>(EpgBouquetNavSavedKeys.WAITING_FOR_PICKER))
    }

    @Test
    fun nullTimeRemovesKeyAndStoresWaitingFalse() {
        val handle = SavedStateHandle(
            mapOf(
                EpgBouquetNavSavedKeys.TIME_SEC to 1_700_000_000L,
                EpgBouquetNavSavedKeys.WAITING_FOR_PICKER to true
            )
        )
        EpgBouquetNavSaved(
            bouquetRef = "1:7:1:B",
            bouquetName = "Favourites",
            timeSec = null,
            waitingForPicker = false
        ).writeTo(handle)
        assertFalse(handle.contains(EpgBouquetNavSavedKeys.TIME_SEC))
        assertTrue(handle.contains(EpgBouquetNavSavedKeys.WAITING_FOR_PICKER))
        assertEquals(false, handle.get<Any>(EpgBouquetNavSavedKeys.WAITING_FOR_PICKER))
        val saved = readEpgBouquetNavSaved(handle)
        assertNull(saved.timeSec)
        assertFalse(saved.waitingForPicker)
        assertEquals("1:7:1:B", saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
    }
}
