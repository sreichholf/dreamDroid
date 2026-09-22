package net.reichholf.dreamdroid.ui.pick

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimerServicePickSavedTest {
    @Test
    fun emptyRead() {
        val values = mutableMapOf<String, Any>()
        val access = MapTimerServicePickSavedAccess(values)
        val saved = readTimerServicePickSaved(access)
        assertEquals("", saved.bouquetRef)
        assertEquals("", saved.bouquetName)
        assertTrue(values.isEmpty())
        assertFalse(values.containsKey(TimerServicePickSavedKeys.BOUQUET_REF))
        assertFalse(values.containsKey(TimerServicePickSavedKeys.BOUQUET_NAME))
    }

    @Test
    fun roundTripRefAndName() {
        val values = mutableMapOf<String, Any>()
        val access = MapTimerServicePickSavedAccess(values)
        TimerServicePickSaved(
            bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
            bouquetName = "Favourites"
        ).writeTo(access)
        val saved = readTimerServicePickSaved(access)
        assertEquals("1:7:1:0:0:0:0:0:0:0:", saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
        assertEquals("1:7:1:0:0:0:0:0:0:0:", values[TimerServicePickSavedKeys.BOUQUET_REF])
        assertEquals("Favourites", values[TimerServicePickSavedKeys.BOUQUET_NAME])
    }
}
