package net.reichholf.dreamdroid.room

import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PiconSeedTest {
    @Test
    fun tvWithoutTheKeySeedsOnlinePiconsAs1xShowedThem() {
        val preferences = MemorySharedPreferences()

        PiconSeed.writeMissingTvDefaults(preferences, isTelevision = true)

        assertTrue(preferences.getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false))
        val seed = PiconSeed.from(preferences)
        assertTrue(seed.online)
        assertFalse(seed.onlineUseName)
        assertEquals(Profile.DEFAULT_PICON_PATH, seed.onlinePath)
    }

    @Test
    fun phoneWithoutTheKeySeedsSyncedPicons() {
        val preferences = MemorySharedPreferences()

        PiconSeed.writeMissingTvDefaults(preferences, isTelevision = false)

        assertFalse(preferences.getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, true))
        assertFalse(PiconSeed.from(preferences).online)
    }

    @Test
    fun aBlankRemotePathSeedsTheDefaultPath() {
        val preferences = MemorySharedPreferences()
        preferences.edit().putString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, " ").commit()

        assertEquals(Profile.DEFAULT_PICON_PATH, PiconSeed.from(preferences).onlinePath)
    }

    @Test
    fun aStoredChoiceIsKept() {
        val preferences = MemorySharedPreferences()
        preferences.edit()
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ONLINE, false)
            .commit()

        PiconSeed.writeMissingTvDefaults(preferences, isTelevision = true)

        assertFalse(preferences.getBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, true))
        assertFalse(PiconSeed.from(preferences).online)
    }
}
