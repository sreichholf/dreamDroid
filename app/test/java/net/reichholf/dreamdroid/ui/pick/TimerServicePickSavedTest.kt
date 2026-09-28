package net.reichholf.dreamdroid.ui.pick

import androidx.lifecycle.SavedStateHandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimerServicePickSavedTest {
    @Test
    fun emptyRead() {
        val handle = SavedStateHandle()
        val saved = readTimerServicePickSaved(handle)
        assertEquals("", saved.bouquetRef)
        assertEquals("", saved.bouquetName)
        assertTrue(handle.keys().isEmpty())
        assertFalse(handle.contains(TimerServicePickSavedKeys.BOUQUET_REF))
        assertFalse(handle.contains(TimerServicePickSavedKeys.BOUQUET_NAME))
    }

    @Test
    fun roundTripRefAndName() {
        val handle = SavedStateHandle()
        TimerServicePickSaved(
            bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
            bouquetName = "Favourites"
        ).writeTo(handle)
        val saved = readTimerServicePickSaved(handle)
        assertEquals("1:7:1:0:0:0:0:0:0:0:", saved.bouquetRef)
        assertEquals("Favourites", saved.bouquetName)
        assertEquals("1:7:1:0:0:0:0:0:0:0:", handle.get<Any>(TimerServicePickSavedKeys.BOUQUET_REF))
        assertEquals("Favourites", handle.get<Any>(TimerServicePickSavedKeys.BOUQUET_NAME))
    }
}
