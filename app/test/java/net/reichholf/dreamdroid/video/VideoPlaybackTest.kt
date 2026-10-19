package net.reichholf.dreamdroid.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoPlaybackTest {
    @Test
    fun theHardwareDecoderGetsTheBufferAndTwoAndAHalfSeconds() {
        assertEquals(4_500L, VideoPlayback.noVideoTimeoutMs(2_000))
    }

    @Test
    fun media3PlaysHttpStreamsOnly() {
        assertEquals(Engine.Media3, VideoPlayback.engineFor(Engine.Media3, "http"))
        assertEquals(Engine.Media3, VideoPlayback.engineFor(Engine.Media3, "HTTPS"))
        assertEquals(Engine.Vlc, VideoPlayback.engineFor(Engine.Media3, "rtsp"))
        assertEquals(Engine.Vlc, VideoPlayback.engineFor(Engine.Vlc, "http"))
    }

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
    fun pausesOnlyWhilePlayingAtNormalSpeed() {
        assertTrue(VideoPlayback.shouldTogglePause(true, 1.0f))
        assertFalse(VideoPlayback.shouldTogglePause(false, 1.0f))
        assertFalse(VideoPlayback.shouldTogglePause(true, 2.0f))
    }

    @Test
    fun detachIsNoOpWhenPlayerMissingOrViewsAlreadyDetached() {
        assertFalse(VideoPlayback.shouldDetachViews(playerPresent = false, viewsAttached = true))
        assertFalse(VideoPlayback.shouldDetachViews(playerPresent = true, viewsAttached = false))
        assertFalse(VideoPlayback.shouldDetachViews(playerPresent = false, viewsAttached = false))
        assertTrue(VideoPlayback.shouldDetachViews(playerPresent = true, viewsAttached = true))
    }

    @Test
    fun actionViewReplacesStalePipOverlayRefs() {
        val fromPip =
            VideoPlayback.OverlayPlaybackExtras("CNN", "1:0:1:cnn", "bouquet-news")
        val incoming =
            VideoPlayback.overlayExtrasForActionView("BBC", "1:0:1:bbc", "bouquet-bbc")
        assertEquals("BBC", incoming.title)
        assertEquals("1:0:1:bbc", incoming.serviceRef)
        assertEquals("bouquet-bbc", incoming.bouquetRef)
        assertEquals("CNN", fromPip.title)
    }

    @Test
    fun recordingActionViewDropsLiveZapRefs() {
        val recording =
            VideoPlayback.overlayExtrasForActionView("Film", serviceRef = null, bouquetRef = null)
        assertEquals("Film", recording.title)
        assertNull(recording.serviceRef)
        assertNull(recording.bouquetRef)
    }
}
