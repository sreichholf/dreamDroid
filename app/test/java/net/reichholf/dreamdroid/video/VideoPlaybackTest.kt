package net.reichholf.dreamdroid.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VideoPlaybackTest {
    @Test
    fun theHardwareDecoderGetsTheBufferAndTwoAndAHalfSeconds() {
        assertEquals(4_500L, VideoPlayback.noVideoTimeoutMs(2_000))
    }

    @Test
    fun aNewStreamGetsThreeSecondsForItsTracksAReopenedOneTen() {
        assertEquals(3_000L, VideoPlayback.tracksTimeoutMs(reopened = false))
        assertEquals(10_000L, VideoPlayback.tracksTimeoutMs(reopened = true))
    }

    @Test
    fun aStreamWithTracksGetsTheBufferAndFiveSecondsToPlay() {
        assertEquals(7_000L, VideoPlayback.playTimeoutMs(2_000))
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
}
