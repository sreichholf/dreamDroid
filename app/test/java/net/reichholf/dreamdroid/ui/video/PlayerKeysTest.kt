package net.reichholf.dreamdroid.ui.video

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun mediaKeysMapToExplicitPlayerActions() {
        assertEquals(PlayerMediaKey.Play, playerMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, 0))
        assertEquals(PlayerMediaKey.Pause, playerMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        assertEquals(
            PlayerMediaKey.TogglePlayPause,
            playerMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0)
        )
        assertEquals(
            PlayerMediaKey.TogglePlayPause,
            playerMediaKey(KeyEvent.KEYCODE_HEADSETHOOK, 0)
        )
        assertEquals(PlayerMediaKey.Stop, playerMediaKey(KeyEvent.KEYCODE_MEDIA_STOP, 0))
    }

    @Test
    fun otherKeysAreNotMediaKeys() {
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_BACK
        ).forEach { assertEquals(PlayerMediaKey.None, playerMediaKey(it, 0), "key $it") }
    }

    @Test
    fun heldToggleAndStopActOnlyOnTheFirstPress() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_STOP
        ).forEach { assertEquals(PlayerMediaKey.Repeat, playerMediaKey(it, 1), "key $it") }
    }

    @Test
    fun heldPlayOrPauseRepeatsItsIdempotentAction() {
        assertEquals(PlayerMediaKey.Play, playerMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY, 3))
        assertEquals(PlayerMediaKey.Pause, playerMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE, 3))
    }
}
