package net.reichholf.dreamdroid.video

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionPlaybackTest {
    @Test
    fun aLiveStreamIsNotSeekableAndHasNoDuration() {
        val playback = sessionPlayback(
            PlayerState(isPlaying = true, timeMs = 5_000),
            StreamPhase.Ready,
            "Das Erste"
        )
        assertTrue(playback.playWhenReady)
        assertEquals(Player.STATE_READY, playback.playbackState)
        assertFalse(playback.seekable)
        assertEquals(C.TIME_UNSET, playback.durationMs)
        assertEquals("Das Erste", playback.title)
    }

    @Test
    fun aSeekableRecordingCarriesItsDuration() {
        val playback = sessionPlayback(
            PlayerState(isPlaying = false, seekable = true, lengthMs = 60_000),
            StreamPhase.Ready,
            ""
        )
        assertFalse(playback.playWhenReady)
        assertTrue(playback.seekable)
        assertEquals(60_000, playback.durationMs)
    }

    @Test
    fun seekableWithoutALengthIsNotSeekable() {
        val playback = sessionPlayback(PlayerState(seekable = true), StreamPhase.Ready, "")
        assertFalse(playback.seekable)
    }

    @Test
    fun thePhaseMapsToThePlaybackState() {
        assertEquals(
            Player.STATE_BUFFERING,
            sessionPlayback(PlayerState(), StreamPhase.Opening, "").playbackState
        )
        assertEquals(
            Player.STATE_ENDED,
            sessionPlayback(PlayerState(), StreamPhase.Ended, "").playbackState
        )
    }

    @Test
    fun positionAloneDoesNotChangeTheMapping() {
        val a = sessionPlayback(PlayerState(isPlaying = true, timeMs = 1), StreamPhase.Ready, "")
        val b = sessionPlayback(PlayerState(isPlaying = true, timeMs = 2), StreamPhase.Ready, "")
        assertEquals(a, b)
    }
}
