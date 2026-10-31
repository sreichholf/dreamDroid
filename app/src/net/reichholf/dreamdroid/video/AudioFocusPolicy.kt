package net.reichholf.dreamdroid.video

/** An audio focus change, as `AudioManager.OnAudioFocusChangeListener` reports it. */
enum class FocusChange {
    Gain,
    Loss,
    LossTransient,

    /** The system ducks for the player (`setWillPauseWhenDucked(false)`); nothing to do. */
    LossTransientCanDuck
}

/** What [AudioFocusPolicy] asks [PlaybackAudioFocus] to do. */
sealed interface FocusCommand {
    data object Request : FocusCommand

    data object Abandon : FocusCommand

    data object Play : FocusCommand

    data object Pause : FocusCommand

    /** Listen for `ACTION_AUDIO_BECOMING_NOISY` or stop listening. */
    data class ListenNoisy(val listen: Boolean) : FocusCommand
}

/**
 * Audio focus and headphone-unplug decisions for the integrated player: events in, commands
 * out. Focus is requested when playback starts and held until [onReleased]. A transient loss
 * that paused the player resumes it on gain, unless the user played or paused in between.
 */
class AudioFocusPolicy {
    private var hasFocus = false
    private var requested = false
    private var playing = false
    private var resumeOnGain = false

    fun onPlayingChanged(playing: Boolean): List<FocusCommand> {
        if (playing == this.playing) return emptyList()
        this.playing = playing
        if (!playing) return listOf(FocusCommand.ListenNoisy(false))
        resumeOnGain = false
        return buildList {
            add(FocusCommand.ListenNoisy(true))
            if (!hasFocus) {
                requested = true
                add(FocusCommand.Request)
            }
        }
    }

    fun onRequestResult(granted: Boolean): List<FocusCommand> {
        hasFocus = granted
        return if (granted) emptyList() else listOf(FocusCommand.Pause)
    }

    fun onFocusChange(change: FocusChange): List<FocusCommand> = when (change) {
        FocusChange.Gain -> {
            hasFocus = true
            val resume = resumeOnGain
            resumeOnGain = false
            if (resume && !playing) listOf(FocusCommand.Play) else emptyList()
        }

        FocusChange.Loss -> {
            hasFocus = false
            resumeOnGain = false
            pauseIfPlaying()
        }

        FocusChange.LossTransient -> {
            hasFocus = false
            resumeOnGain = playing
            pauseIfPlaying()
        }

        FocusChange.LossTransientCanDuck -> emptyList()
    }

    /** The user pressed play or pause: a later gain does not overrule them. */
    fun onUserPlayPause(): List<FocusCommand> {
        resumeOnGain = false
        return emptyList()
    }

    fun onBecomingNoisy(): List<FocusCommand> {
        resumeOnGain = false
        return pauseIfPlaying()
    }

    fun onReleased(): List<FocusCommand> {
        val held = requested
        requested = false
        hasFocus = false
        resumeOnGain = false
        val listening = playing
        playing = false
        return buildList {
            if (listening) add(FocusCommand.ListenNoisy(false))
            if (held) add(FocusCommand.Abandon)
        }
    }

    private fun pauseIfPlaying(): List<FocusCommand> =
        if (playing) listOf(FocusCommand.Pause) else emptyList()
}
