package net.reichholf.dreamdroid.ui.epg

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
        val values = mutableMapOf<String, Any>()
        val access = MapEpgBouquetNavSavedAccess(values)
        val saved = readEpgBouquetNavSaved(access)
        assertEquals("", saved.bouquetRef)
        assertEquals("", saved.bouquetName)
        assertNull(saved.timeSec)
        assertFalse(saved.waitingForPicker)
        assertTrue(values.isEmpty())
        assertFalse(values.containsKey(EpgBouquetNavSavedKeys.BOUQUET_REF))
        assertFalse(values.containsKey(EpgBouquetNavSavedKeys.BOUQUET_NAME))
        assertFalse(values.containsKey(EpgBouquetNavSavedKeys.TIME_SEC))
        assertFalse(values.containsKey(EpgBouquetNavSavedKeys.WAITING_FOR_PICKER))
    }

    @Test
    fun roundTripKeepsTimeSecZero() {
        val values = mutableMapOf<String, Any>()
        val access = MapEpgBouquetNavSavedAccess(values)
        EpgBouquetNavSaved(
            bouquetRef = "1:7:1:B",
            bouquetName = "Favourites",
            timeSec = 0L,
            waitingForPicker = true
        ).writeTo(access)
        val saved = readEpgBouquetNavSaved(access)
        assertEquals("1:7:1:B", saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
        assertEquals(0L, saved.timeSec)
        assertTrue(saved.waitingForPicker)
        assertEquals("1:7:1:B", values[EpgBouquetNavSavedKeys.BOUQUET_REF])
        assertEquals("Favourites", values[EpgBouquetNavSavedKeys.BOUQUET_NAME])
        assertTrue(values.containsKey(EpgBouquetNavSavedKeys.TIME_SEC))
        assertEquals(0L, values[EpgBouquetNavSavedKeys.TIME_SEC])
        assertEquals(true, values[EpgBouquetNavSavedKeys.WAITING_FOR_PICKER])
    }

    @Test
    fun nullTimeRemovesKeyAndStoresWaitingFalse() {
        val values = mutableMapOf<String, Any>(
            EpgBouquetNavSavedKeys.TIME_SEC to 1_700_000_000L,
            EpgBouquetNavSavedKeys.WAITING_FOR_PICKER to true
        )
        val access = MapEpgBouquetNavSavedAccess(values)
        EpgBouquetNavSaved(
            bouquetRef = "1:7:1:B",
            bouquetName = "Favourites",
            timeSec = null,
            waitingForPicker = false
        ).writeTo(access)
        assertFalse(values.containsKey(EpgBouquetNavSavedKeys.TIME_SEC))
        assertTrue(values.containsKey(EpgBouquetNavSavedKeys.WAITING_FOR_PICKER))
        assertEquals(false, values[EpgBouquetNavSavedKeys.WAITING_FOR_PICKER])
        val saved = readEpgBouquetNavSaved(access)
        assertNull(saved.timeSec)
        assertFalse(saved.waitingForPicker)
        assertEquals("1:7:1:B", saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
    }
}
