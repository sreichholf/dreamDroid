package net.reichholf.dreamdroid.ui.zap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZapNavSavedTest {
    @Test
    fun absentReadsProfileDefaultsWithoutStoring() {
        val values = mutableMapOf<String, Any>()
        val access = MapZapNavSavedAccess(values)
        val saved = readZapNavSaved(
            access,
            defaultBouquetRef = "1:7:1:0:0:0:0:0:0:0:",
            defaultBouquetName = "Favourites (TV)"
        )
        assertEquals("1:7:1:0:0:0:0:0:0:0:", saved.bouquetRef)
        assertEquals("Favourites (TV)", saved.bouquetName)
        assertFalse(saved.waitingForPicker)
        assertTrue(values.isEmpty())
        assertFalse(values.containsKey(ZapNavSavedKeys.BOUQUET_REF))
        assertFalse(values.containsKey(ZapNavSavedKeys.BOUQUET_NAME))
        assertFalse(values.containsKey(ZapNavSavedKeys.WAITING_FOR_PICKER))
    }

    @Test
    fun roundTrip() {
        val values = mutableMapOf<String, Any>()
        val access = MapZapNavSavedAccess(values)
        ZapNavSaved(
            bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
            bouquetName = "Favourites",
            waitingForPicker = true
        ).writeTo(access)
        val saved = readZapNavSaved(
            access,
            defaultBouquetRef = "profile-ref",
            defaultBouquetName = "Profile"
        )
        assertEquals("1:7:1:0:0:0:0:0:0:0:", saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
        assertTrue(saved.waitingForPicker)
        assertEquals("1:7:1:0:0:0:0:0:0:0:", values[ZapNavSavedKeys.BOUQUET_REF])
        assertEquals("Favourites", values[ZapNavSavedKeys.BOUQUET_NAME])
        assertEquals(true, values[ZapNavSavedKeys.WAITING_FOR_PICKER])
    }

    @Test
    fun waitingForPickerFalseIsStoredAfterWrite() {
        val values = mutableMapOf<String, Any>(
            ZapNavSavedKeys.WAITING_FOR_PICKER to true
        )
        val access = MapZapNavSavedAccess(values)
        ZapNavSaved(
            bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
            bouquetName = "Favourites",
            waitingForPicker = false
        ).writeTo(access)
        assertTrue(values.containsKey(ZapNavSavedKeys.WAITING_FOR_PICKER))
        assertEquals(false, values[ZapNavSavedKeys.WAITING_FOR_PICKER])
        val saved = readZapNavSaved(
            access,
            defaultBouquetRef = "profile-ref",
            defaultBouquetName = "Profile"
        )
        assertFalse(saved.waitingForPicker)
    }
}
