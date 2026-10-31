package net.reichholf.dreamdroid.video

import android.os.Build
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackPipStateTest {
    @Test
    fun nothingPlaysBeforeTheFirstPlaying() {
        val state = PlaybackPipState()
        assertFalse(state.onEvent(PlayerEvent.Opening))
        assertFalse(state.autoEnter)
        assertFalse(state.playbackStarted)
        assertFalse(state.shouldEnterOnLeaveHint(Build.VERSION_CODES.R, false))
    }

    @Test
    fun playingTurnsAutoEnterOnAndStartsPlayback() {
        val state = PlaybackPipState()
        assertTrue(state.onEvent(PlayerEvent.Playing))
        assertTrue(state.autoEnter)
        assertTrue(state.playbackStarted)
    }

    @Test
    fun aLiveStreamKeepsAutoEnterAcrossAZap() {
        val state = PlaybackPipState()
        state.onEvent(PlayerEvent.Playing)
        listOf(
            PlayerEvent.Opening,
            PlayerEvent.Progress,
            PlayerEvent.TracksFound,
            PlayerEvent.Playing
        ).forEach { event ->
            assertFalse(state.onEvent(event), "$event changed auto-enter")
            assertTrue(state.autoEnter, "auto-enter off after $event")
        }
    }

    @Test
    fun endFailureAndReleaseTurnAutoEnterOffButKeepPlaybackStarted() {
        listOf<(PlaybackPipState) -> Boolean>(
            { it.onEvent(PlayerEvent.Ended) },
            { it.onEvent(PlayerEvent.Failed()) },
            { it.onStopped() }
        ).forEach { stop ->
            val state = PlaybackPipState()
            state.onEvent(PlayerEvent.Playing)
            assertTrue(stop(state))
            assertFalse(state.autoEnter)
            assertTrue(state.playbackStarted)
            assertFalse(stop(state), "stopping twice changed auto-enter")
        }
    }

    @Test
    fun aFailureBeforePlayingDoesNotStartPlayback() {
        val state = PlaybackPipState()
        assertFalse(state.onEvent(PlayerEvent.Failed()))
        assertFalse(state.playbackStarted)
    }

    @Test
    fun playbackStartedSurvivesSaveAndRestore() {
        val state = PlaybackPipState()
        state.onEvent(PlayerEvent.Playing)
        state.onStopped()
        val restored = PlaybackPipState(playbackStarted = state.playbackStarted)
        assertTrue(restored.playbackStarted)
        assertFalse(restored.autoEnter)
        assertFalse(PlaybackPipState(playbackStarted = false).playbackStarted)
    }

    @Test
    fun theLeaveHintEntersPipOnlyBelowSWhileAStreamPlaysOutsidePip() {
        val state = PlaybackPipState()
        state.onEvent(PlayerEvent.Playing)
        assertTrue(state.shouldEnterOnLeaveHint(Build.VERSION_CODES.R, false))
        assertFalse(state.shouldEnterOnLeaveHint(Build.VERSION_CODES.R, true))
        assertFalse(state.shouldEnterOnLeaveHint(Build.VERSION_CODES.S, false))
        state.onStopped()
        assertFalse(state.shouldEnterOnLeaveHint(Build.VERSION_CODES.R, false))
    }
}
