package net.reichholf.dreamdroid.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decides when one stream's picture is ready: at its first video frame, at once for a stream
 * without video, or [PlayerEvent.NoVideo] when the video shows no frame within [timeoutMs].
 * One per stream; [cancel] it when the stream's player is released.
 */
class FirstPictureWatch(
    private val scope: CoroutineScope,
    private val timeoutMs: Long,
    private val emit: (PlayerEvent) -> Unit
) {
    private var pictureReady = false
    private var timeout: Job? = null

    /** [hasVideo]: a video track is selected. Only the first call decides. */
    fun onTracks(hasVideo: Boolean) {
        if (pictureReady || timeout != null) return
        if (hasVideo) {
            timeout = scope.launch {
                delay(timeoutMs)
                if (!pictureReady) emit(PlayerEvent.NoVideo)
            }
        } else {
            pictureReady = true
            emit(PlayerEvent.PictureReady)
        }
    }

    fun onFirstFrame() {
        if (pictureReady) return
        pictureReady = true
        timeout?.cancel()
        emit(PlayerEvent.PictureReady)
    }

    /** Stops the watch for good; later calls report nothing. */
    fun cancel() {
        pictureReady = true
        timeout?.cancel()
    }
}
