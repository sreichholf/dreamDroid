package net.reichholf.dreamdroid.video

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioFocusPolicyTest {
    /** Runs the policy the way [PlaybackAudioFocus] does; Play and Pause flip the player. */
    private class Harness(private val grant: Boolean = true) {
        val policy = AudioFocusPolicy()
        val log = mutableListOf<FocusCommand>()
        var playing = false
        var noisy = false

        fun run(commands: List<FocusCommand>) {
            commands.forEach { command ->
                log += command
                when (command) {
                    FocusCommand.Request -> run(policy.onRequestResult(grant))
                    FocusCommand.Play -> playerPlays(true)
                    FocusCommand.Pause -> playerPlays(false)
                    is FocusCommand.ListenNoisy -> noisy = command.listen
                    FocusCommand.Abandon -> Unit
                }
            }
        }

        fun playerPlays(playing: Boolean) {
            this.playing = playing
            run(policy.onPlayingChanged(playing))
        }

        fun focus(change: FocusChange) = run(policy.onFocusChange(change))
    }

    @Test
    fun playbackRequestsFocusOnceAndListensForNoisy() {
        val h = Harness()
        h.playerPlays(true)
        h.playerPlays(false)
        h.playerPlays(true)
        assertEquals(1, h.log.count { it == FocusCommand.Request })
        assertEquals(true, h.noisy)
    }

    @Test
    fun aDeniedRequestPauses() {
        val h = Harness(grant = false)
        h.playerPlays(true)
        assertEquals(false, h.playing)
        assertEquals(false, h.noisy)
    }

    @Test
    fun aLossPausesAndGainDoesNotResume() {
        val h = Harness()
        h.playerPlays(true)
        h.focus(FocusChange.Loss)
        assertEquals(false, h.playing)
        h.focus(FocusChange.Gain)
        assertEquals(false, h.playing)
    }

    @Test
    fun playAfterALossRequestsFocusAgain() {
        val h = Harness()
        h.playerPlays(true)
        h.focus(FocusChange.Loss)
        h.playerPlays(true)
        assertEquals(2, h.log.count { it == FocusCommand.Request })
    }

    @Test
    fun aTransientLossResumesOnGain() {
        val h = Harness()
        h.playerPlays(true)
        h.focus(FocusChange.LossTransient)
        assertEquals(false, h.playing)
        h.focus(FocusChange.Gain)
        assertEquals(true, h.playing)
        assertEquals(1, h.log.count { it == FocusCommand.Request })
    }

    @Test
    fun aTransientLossWhilePausedDoesNotResume() {
        val h = Harness()
        h.playerPlays(true)
        h.playerPlays(false)
        h.focus(FocusChange.LossTransient)
        h.focus(FocusChange.Gain)
        assertEquals(false, h.playing)
    }

    @Test
    fun aUserPauseBetweenLossAndGainSuppressesTheResume() {
        val h = Harness()
        h.playerPlays(true)
        h.focus(FocusChange.LossTransient)
        h.run(h.policy.onUserPlayPause())
        h.focus(FocusChange.Gain)
        assertEquals(false, h.playing)
    }

    @Test
    fun aUserPlayBetweenLossAndGainSuppressesTheResume() {
        val h = Harness()
        h.playerPlays(true)
        h.focus(FocusChange.LossTransient)
        h.playerPlays(true)
        h.playerPlays(false)
        val before = h.log.size
        h.focus(FocusChange.Gain)
        assertEquals(false, h.playing)
        assertEquals(emptyList<FocusCommand>(), h.log.drop(before))
    }

    @Test
    fun duckingIsLeftToTheSystem() {
        val h = Harness()
        h.playerPlays(true)
        h.focus(FocusChange.LossTransientCanDuck)
        assertEquals(true, h.playing)
    }

    @Test
    fun becomingNoisyPausesAndSuppressesAResume() {
        val h = Harness()
        h.playerPlays(true)
        h.run(h.policy.onBecomingNoisy())
        assertEquals(false, h.playing)
        assertEquals(false, h.noisy)

        h.playerPlays(true)
        h.focus(FocusChange.LossTransient)
        h.run(h.policy.onBecomingNoisy())
        h.focus(FocusChange.Gain)
        assertEquals(false, h.playing)
    }

    @Test
    fun releaseAbandonsFocusAndStopsListening() {
        val h = Harness()
        h.playerPlays(true)
        h.run(h.policy.onReleased())
        assertEquals(FocusCommand.Abandon, h.log.last())
        assertEquals(false, h.noisy)
    }

    @Test
    fun releaseWithoutARequestAbandonsNothing() {
        val h = Harness()
        h.run(h.policy.onReleased())
        assertEquals(emptyList<FocusCommand>(), h.log)
    }
}
