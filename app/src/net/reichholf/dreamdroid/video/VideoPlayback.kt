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

    fun shouldTogglePause(isPlaying: Boolean, rate: Float, sameMedia: Boolean): Boolean =
        sameMedia && isPlaying && rate == 1.0f

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

    /**
     * Zap must persist title, service ref, and bouquet ref so a later overlay
     * rebind or restore does not keep the previous channel.
     */
    fun overlayExtrasForZap(
        title: String?,
        serviceRef: String?,
        bouquetRef: String?
    ): OverlayPlaybackExtras = OverlayPlaybackExtras(title, serviceRef, bouquetRef)
}
