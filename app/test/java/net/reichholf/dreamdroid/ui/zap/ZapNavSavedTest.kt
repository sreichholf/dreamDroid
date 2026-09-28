package net.reichholf.dreamdroid.ui.zap

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZapNavSavedTest {
    @Test
    fun absentReadsProfileDefaultsWithoutStoring() {
        val handle = SavedStateHandle()
        val saved = readZapNavSaved(
            handle,
            defaultBouquetRef = "1:7:1:0:0:0:0:0:0:0:",
            defaultBouquetName = "Favourites (TV)"
        )
        assertEquals("1:7:1:0:0:0:0:0:0:0:", saved.bouquetRef)
        assertEquals("Favourites (TV)", saved.bouquetName)
        assertFalse(saved.waitingForPicker)
        assertTrue(handle.keys().isEmpty())
        assertFalse(handle.contains(ZapNavSavedKeys.BOUQUET_REF))
        assertFalse(handle.contains(ZapNavSavedKeys.BOUQUET_NAME))
        assertFalse(handle.contains(ZapNavSavedKeys.WAITING_FOR_PICKER))
    }

    @Test
    fun roundTrip() {
        val handle = SavedStateHandle()
        ZapNavSaved(
            bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
            bouquetName = "Favourites",
            waitingForPicker = true
        ).writeTo(handle)
        val saved = readZapNavSaved(
            handle,
            defaultBouquetRef = "profile-ref",
            defaultBouquetName = "Profile"
        )
        assertEquals("1:7:1:0:0:0:0:0:0:0:", saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
        assertTrue(saved.waitingForPicker)
        assertEquals("1:7:1:0:0:0:0:0:0:0:", handle.get<Any>(ZapNavSavedKeys.BOUQUET_REF))
        assertEquals("Favourites", handle.get<Any>(ZapNavSavedKeys.BOUQUET_NAME))
        assertEquals(true, handle.get<Any>(ZapNavSavedKeys.WAITING_FOR_PICKER))
    }

    @Test
    fun waitingForPickerFalseIsStoredAfterWrite() {
        val handle = SavedStateHandle(
            mapOf(
                ZapNavSavedKeys.WAITING_FOR_PICKER to true
            )
        )
        ZapNavSaved(
            bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
            bouquetName = "Favourites",
            waitingForPicker = false
        ).writeTo(handle)
        assertTrue(handle.contains(ZapNavSavedKeys.WAITING_FOR_PICKER))
        assertEquals(false, handle.get<Any>(ZapNavSavedKeys.WAITING_FOR_PICKER))
        val saved = readZapNavSaved(
            handle,
            defaultBouquetRef = "profile-ref",
            defaultBouquetName = "Profile"
        )
        assertFalse(saved.waitingForPicker)
    }
}
