package net.reichholf.dreamdroid.data

import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LastLiveStreamStoreTest {
    private val preferences = MemorySharedPreferences()
    private val store = LastLiveStreamStore(preferences)

    @Test
    fun returnsTheLastLiveChannelForTheActiveProfile() {
        preferences.edit().putInt(DreamDroid.CURRENT_PROFILE, 4).apply()
        store.save(
            reference = "1:0:1:1:2:3:4:0:0:0:",
            title = "News",
            bouquetRef = "1:7:1:0:0:0:0:0:0:0:"
        )

        assertEquals(
            LastLiveStream(
                reference = "1:0:1:1:2:3:4:0:0:0:",
                title = "News",
                bouquetRef = "1:7:1:0:0:0:0:0:0:0:"
            ),
            store.getForActiveProfile()
        )
    }

    @Test
    fun doesNotRestoreAChannelSavedForAnotherProfile() {
        preferences.edit().putInt(DreamDroid.CURRENT_PROFILE, 4).apply()
        store.save("service-ref", "News", null)
        preferences.edit().putInt(DreamDroid.CURRENT_PROFILE, 5).apply()

        assertNull(store.getForActiveProfile())
    }

    @Test
    fun blankServiceReferencesAreNotSavedAsLastStream() {
        store.save("", "News", null)

        assertNull(store.getForActiveProfile())
    }
}
