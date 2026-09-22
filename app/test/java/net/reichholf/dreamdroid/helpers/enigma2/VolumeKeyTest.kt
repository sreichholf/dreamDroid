package net.reichholf.dreamdroid.helpers.enigma2

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VolumeKeyTest {
    @Test
    fun prefOffNeverConsumes() {
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_VOLUME_UP, false))
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN, false))
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_VOLUME_MUTE, false))
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_BACK, false))
    }

    @Test
    fun prefOnConsumesOnlyVolumeUpAndDown() {
        assertTrue(shouldConsumeVolumeKey(KeyEvent.KEYCODE_VOLUME_UP, true))
        assertTrue(shouldConsumeVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN, true))
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_VOLUME_MUTE, true))
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_BACK, true))
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_DPAD_CENTER, true))
    }

    @Test
    fun otherKeysAreNeverConsumed() {
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_VOLUME_MUTE, true))
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_VOLUME_MUTE, false))
        assertFalse(shouldConsumeVolumeKey(KeyEvent.KEYCODE_MEDIA_PLAY, true))
    }

    @Test
    fun commandsMatchVolumeConstants() {
        assertEquals(Volume.CMD_UP, volumeCommandForKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(Volume.CMD_DOWN, volumeCommandForKey(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(volumeCommandForKey(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertNull(volumeCommandForKey(KeyEvent.KEYCODE_BACK))
    }
}
