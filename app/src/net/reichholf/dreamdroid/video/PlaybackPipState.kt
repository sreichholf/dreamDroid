package net.reichholf.dreamdroid.video

import android.os.Build

/**
 * What PiP and the TV session gate need to know of the stream in VideoActivity. Only
 * [playbackStarted] survives activity recreation; the player is released in onStop anyway.
 */
class PlaybackPipState(playbackStarted: Boolean = false) {
    /** A stream plays now. */
    var streamPlaying: Boolean = false
        private set

    /** A stream reached Playing in this activity; TV keeps the activity up when Offline. */
    var playbackStarted: Boolean = playbackStarted
        private set

    /** PiP auto-enters (S+) only while a stream plays. */
    val autoEnter: Boolean get() = streamPlaying

    /** True when [event] changed [autoEnter], so the PiP params need re-sending. */
    fun onEvent(event: PlayerEvent): Boolean = when (event) {
        PlayerEvent.Playing -> {
            playbackStarted = true
            setPlaying(true)
        }

        is PlayerEvent.Failed, PlayerEvent.Ended -> setPlaying(false)

        else -> false
    }

    /** The player was released. True when [autoEnter] changed. */
    fun onStopped(): Boolean = setPlaying(false)

    /** Below S nothing auto-enters, so leaving the app enters PiP by hand. */
    fun shouldEnterOnLeaveHint(sdkInt: Int, inPictureInPicture: Boolean): Boolean =
        sdkInt < Build.VERSION_CODES.S && streamPlaying && !inPictureInPicture

    private fun setPlaying(playing: Boolean): Boolean {
        if (streamPlaying == playing) return false
        streamPlaying = playing
        return true
    }
}
