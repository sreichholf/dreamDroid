package net.reichholf.dreamdroid.helpers

import android.view.KeyEvent
import net.reichholf.dreamdroid.helpers.enigma2.Volume
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HardwareVolumeKeysTest {
    @Test
    fun prefOffNeverConsumesVolumeOrOtherKeys() {
        assertFalse(consumesHardwareVolume(false, KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(consumesHardwareVolume(false, KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(consumesHardwareVolume(false, KeyEvent.KEYCODE_VOLUME_MUTE))
        assertFalse(consumesHardwareVolume(false, KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun prefOnConsumesOnlyVolumeUpAndDown() {
        assertTrue(consumesHardwareVolume(true, KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(consumesHardwareVolume(true, KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(consumesHardwareVolume(true, KeyEvent.KEYCODE_VOLUME_MUTE))
        assertFalse(consumesHardwareVolume(true, KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun enigmaVolumeCommandMapsUpAndDown() {
        assertEquals(Volume.CMD_UP, enigmaVolumeCommand(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals("up", enigmaVolumeCommand(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(Volume.CMD_DOWN, enigmaVolumeCommand(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals("down", enigmaVolumeCommand(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertNull(enigmaVolumeCommand(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertNull(enigmaVolumeCommand(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun volumeSetParamsEncodeSetUpAndDown() {
        val up = enigmaVolumeSetParams(KeyEvent.KEYCODE_VOLUME_UP)
        assertEquals("set=up", NameValuePair.toString(up!!))
        val down = enigmaVolumeSetParams(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertEquals("set=down", NameValuePair.toString(down!!))
        assertNull(enigmaVolumeSetParams(KeyEvent.KEYCODE_VOLUME_MUTE))
    }
}
