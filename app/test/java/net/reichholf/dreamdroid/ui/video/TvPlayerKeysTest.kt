package net.reichholf.dreamdroid.ui.video

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TvPlayerKeysTest {
    @Test
    fun bareLiveVideoZapsWithLeftRightAndOpensListWithUpDown() {
        assertEquals(TvPlayerKey.NextChannel, key(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(TvPlayerKey.PreviousChannel, key(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(TvPlayerKey.ChannelList, key(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(TvPlayerKey.ChannelList, key(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(TvPlayerKey.None, key(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun channelKeysZapEvenWithTheOverlayOpen() {
        assertEquals(TvPlayerKey.NextChannel, key(KeyEvent.KEYCODE_CHANNEL_UP, overlay = true))
        assertEquals(
            TvPlayerKey.PreviousChannel,
            key(KeyEvent.KEYCODE_CHANNEL_DOWN, overlay = true)
        )
    }

    @Test
    fun openOverlayLeavesTheDpadToFocus() {
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT
        ).forEach { code ->
            assertEquals(TvPlayerKey.None, key(code, overlay = true))
        }
    }

    @Test
    fun recordingsHaveNoChannelShortcuts() {
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_CHANNEL_UP
        ).forEach { code ->
            assertEquals(TvPlayerKey.None, key(code, live = false))
        }
    }

    private fun key(code: Int, overlay: Boolean = false, live: Boolean = true): TvPlayerKey =
        tvPlayerKey(code, overlayVisible = overlay, live = live)
}
