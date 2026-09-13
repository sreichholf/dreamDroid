package net.reichholf.dreamdroid.video

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoPlaybackTest {
    @Test
    fun nextIndexIncludesLastItemThenWraps() {
        assertEquals(1, VideoPlayback.nextIndex(0, 3))
        assertEquals(2, VideoPlayback.nextIndex(1, 3))
        assertEquals(0, VideoPlayback.nextIndex(2, 3))
        assertEquals(-1, VideoPlayback.nextIndex(-1, 3))
        assertEquals(-1, VideoPlayback.nextIndex(0, 0))
    }

    @Test
    fun previousIndexWrapsFromFirstToLast() {
        assertEquals(2, VideoPlayback.previousIndex(0, 3))
        assertEquals(0, VideoPlayback.previousIndex(1, 3))
    }

    @Test
    fun playUriMustNotTogglePauseOnANewUri() {
        assertFalse(VideoPlayback.shouldTogglePause(true, 1.0f, sameMedia = false))
        assertTrue(VideoPlayback.shouldTogglePause(true, 1.0f, sameMedia = true))
        assertFalse(VideoPlayback.shouldTogglePause(false, 1.0f, sameMedia = true))
        assertFalse(VideoPlayback.shouldTogglePause(true, 2.0f, sameMedia = true))
    }
}
