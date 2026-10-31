package net.reichholf.dreamdroid.ui.video

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerKeysTest {
    @Test
    fun volumeAndOtherSystemKeysAreLeftToTheSystem() {
        listOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST
        ).forEach { assertTrue(isSystemKey(it), "key $it") }
    }

    @Test
    fun playerKeysAreNotSystemKeys() {
        listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_F
        ).forEach { assertFalse(isSystemKey(it), "key $it") }
    }
}
