package net.reichholf.dreamdroid.video

object VideoPlayback {
    data class OverlayPlaybackExtras(
        val title: String?,
        val serviceRef: String?,
        val bouquetRef: String?
    )

    fun nextIndex(current: Int, size: Int): Int {
        if (current < 0 || size <= 0) {
            return -1
        }
        val next = current + 1
        return if (next >= size) 0 else next
    }

    fun previousIndex(current: Int, size: Int): Int {
        if (current < 0 || size <= 0) {
            return -1
        }
        return if (current == 0) size - 1 else current - 1
    }

    /**
     * How long a hardware decoder may take to show the first frame once the stream plays: the
     * buffer, then a short grace for the decoder and a keyframe. A working decoder shows a
     * frame well within it; one that takes DVB streams and outputs nothing never does.
     */
    fun noVideoTimeoutMs(bufferMs: Int): Long = bufferMs + NO_VIDEO_GRACE_MS

    private const val NO_VIDEO_GRACE_MS = 2_500L

    /**
     * How long a live stream may take to show its first audio or video track before it is
     * opened again. A healthy stream shows one within a second. Right after a channel switch
     * the receiver can still send the old service's tables from its buffer first, and VLC then
     * waits for a program that never comes; a reopened stream gets more time, for a receiver
     * that is still tuning.
     */
    fun tracksTimeoutMs(reopened: Boolean): Long =
        if (reopened) REOPENED_TRACKS_TIMEOUT_MS else TRACKS_TIMEOUT_MS

    /** Once the tracks are known, how long a live stream may take to play: the buffer, plus. */
    fun playTimeoutMs(bufferMs: Int): Long = bufferMs + PLAY_GRACE_MS

    private const val TRACKS_TIMEOUT_MS = 3_000L
    private const val REOPENED_TRACKS_TIMEOUT_MS = 10_000L
    private const val PLAY_GRACE_MS = 5_000L

    /** Media3 plays http(s) only; RTSP (the encoder stream mode) and the rest stay on VLC. */
    fun engineFor(preferred: Engine, scheme: String?): Engine =
        if (preferred == Engine.Media3 && scheme?.lowercase() in setOf("http", "https")) {
            Engine.Media3
        } else {
            Engine.Vlc
        }

    fun shouldTogglePause(isPlaying: Boolean, rate: Float): Boolean = isPlaying && rate == 1.0f

    /**
     * [org.videolan.libvlc.interfaces.IVLCVout.detachViews] is not idempotent.
     * Skip it when the player is already gone or views are already detached.
     */
    fun shouldDetachViews(playerPresent: Boolean, viewsAttached: Boolean): Boolean =
        playerPresent && viewsAttached

    /**
     * New [android.content.Intent.ACTION_VIEW] extras replace the previous overlay
     * session (singleTop / PiP). They are not merged, so a recording intent that
     * omits service/bouquet refs drops leftover live zap state.
     */
    fun overlayExtrasForActionView(
        title: String?,
        serviceRef: String?,
        bouquetRef: String?
    ): OverlayPlaybackExtras = OverlayPlaybackExtras(title, serviceRef, bouquetRef)
}
