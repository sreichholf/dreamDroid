package net.reichholf.dreamdroid.video

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.di.ApplicationScope
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout

/** What the overlay shows of the playing stream, kept from libVLC's events. */
data class PlayerState(
    val seekable: Boolean = false,
    val lengthMs: Long = 0,
    val timeMs: Long = 0,
    val position: Float = 0f,
    val audioTracks: Int = 0,
    val subtitleTracks: Int = 0
)

/**
 * The process's libVLC [MediaPlayer].
 *
 * Call it from the main thread. Video views and the event listener are main-thread work.
 * Calls that can wait for a stream to close (setMedia, stop, release, seeking, track and
 * scale changes) go through [commands]. As in VLC for Android, play(), pause, and the track
 * getters run on the main thread, but only once setMedia has returned (see [switching]).
 * Read time, length, and track counts from [state].
 *
 * One wait is left, as in VLC for Android: detaching the views while a stream is still
 * closing makes libVLC disable the video track on the main thread, and that waits too.
 * Leaving the player in the middle of a stuck zap can still be slow.
 */
@Singleton
class VLCPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val commands = PlayerCommandQueue(scope, Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Counts streams on the main thread: [playUri] starts one, [detach] and [release] end it.
     * Events and a pending play() tagged with an older value belong to a stream that is gone.
     */
    private var generation: Int = 0

    /** The [generation] whose setMedia has returned; main thread only. */
    private var startedGeneration: Int = -1

    /**
     * True while setMedia closes the previous stream. libVLC holds the input lock then, so a
     * main-thread call would wait for it.
     */
    private val switching: Boolean get() = startedGeneration != generation
    private var listener: MediaPlayer.EventListener? = null

    private val stateFlow = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = stateFlow.asStateFlow()

    /** Receives libVLC's events on the main thread after [state] took them in. */
    fun setEventListener(listener: MediaPlayer.EventListener?) {
        this.listener = listener
    }

    fun attach(
        newVideoLayoutListener: IVLCVout.OnNewVideoLayoutListener?,
        surfaceView: SurfaceView?,
        subtitleSurfaceView: SurfaceView?
    ) {
        val vlcVout = mediaPlayer().vlcVout
        if (vlcVout.areViewsAttached()) {
            vlcVout.detachViews()
        }
        vlcVout.setVideoView(surfaceView)
        vlcVout.setSubtitlesView(subtitleSurfaceView)
        vlcVout.attachViews(newVideoLayoutListener)
    }

    /** Detaches the views and stops the stream; [release] also frees the player. */
    fun detach() {
        val mp = mediaPlayer ?: return
        val vlcVout = mp.vlcVout
        if (!VideoPlayback.shouldDetachViews(true, vlcVout.areViewsAttached())) {
            return
        }
        generation++
        vlcVout.detachViews()
        commands.post { mp.stop() }
    }

    /** Frees the player. The next call creates a new one. */
    fun release() {
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        generation++
        mp.setEventListener(null)
        if (mp.vlcVout.areViewsAttached()) {
            mp.vlcVout.detachViews()
        }
        commands.post {
            mp.stop()
            mp.release()
        }
        stateFlow.value = PlayerState()
    }

    fun setWindowSize(width: Int, height: Int) {
        mediaPlayer?.vlcVout?.setWindowSize(width, height)
    }

    /** Lets libVLC fit the video to the surface. */
    fun resetScale() {
        val mp = mediaPlayer ?: return
        commands.post {
            mp.setAspectRatio(null)
            mp.setScale(0f)
        }
    }

    fun playUri(uri: Uri, flags: Int) {
        val mp = mediaPlayer()
        val gen = ++generation
        stateFlow.value = PlayerState()
        val isHwAccel = flags and MEDIA_HWACCEL_ENABLED > 0
        val isHwAccelForce = flags and MEDIA_HWACCEL_FORCE > 0
        // VLC for Android's PlayerController.startPlayback: setMedia off main, then listener
        // and play() on main. setMedia joins the old stream's threads, which can take long.
        scope.launch(Dispatchers.Main.immediate) {
            try {
                commands.call {
                    val media = Media(VLCInstance.get(context), uri)
                    try {
                        media.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce)
                        // The old stream's teardown events must not reach the overlay.
                        mp.setEventListener(null)
                        mp.media = media
                    } finally {
                        media.release()
                    }
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Starting $uri failed", e)
                return@launch
            }
            // A zap, detach, or release since then owns the player now.
            if (gen != generation) return@launch
            startedGeneration = gen
            mp.setEventListener(eventListener(mp, gen))
            mp.rate = 1.0f
            mp.play()
        }
    }

    /** Pauses a stream playing at normal speed; otherwise plays at normal speed. */
    fun play() {
        val mp = mediaPlayer ?: return
        if (switching || !mp.hasMedia()) return
        if (VideoPlayback.shouldTogglePause(mp.isPlaying, mp.rate)) {
            mp.pause()
        } else {
            mp.play()
        }
        mp.rate = 1.0f
    }

    fun setPosition(position: Float) {
        val mp = mediaPlayer ?: return
        stateFlow.update { it.copy(position = position) }
        commands.post { mp.position = position }
    }

    /** Rewinds faster each call, down to -64x, while a seekable stream plays. */
    fun slower() {
        val mp = mediaPlayer ?: return
        commands.post {
            if (!mp.isSeekable || !mp.isPlaying) return@post
            var rate = mp.rate
            if (rate == 1.0f) {
                rate = -1.0f
            }
            mp.rate = max(rate * 2, -64f)
        }
    }

    /** Null while no stream plays or one is still switching. */
    fun audioTracks(): Array<MediaPlayer.TrackDescription>? {
        val mp = mediaPlayer ?: return null
        if (switching || !mp.hasMedia()) return null
        return mp.audioTracks
    }

    /** Null while no stream plays or one is still switching. */
    fun subtitleTracks(): Array<MediaPlayer.TrackDescription>? {
        val mp = mediaPlayer ?: return null
        if (switching || !mp.hasMedia()) return null
        return mp.spuTracks
    }

    fun setAudioTrack(id: Int) {
        val mp = mediaPlayer ?: return
        commands.post { mp.setAudioTrack(id) }
    }

    fun setSubtitleTrack(id: Int) {
        val mp = mediaPlayer ?: return
        commands.post { mp.setSpuTrack(id) }
    }

    private fun mediaPlayer(): MediaPlayer = mediaPlayer ?: MediaPlayer(VLCInstance.get(context))
        .also { mp ->
            mediaPlayer = mp
            mp.setEventListener(eventListener(mp, generation))
            // No setVideoTrackEnabled: libVLC turns video on itself once the surfaces are
            // ready. Queued, it would land while a stream starts and pick the track mid-start.
            commands.post {
                mp.setAspectRatio(null)
                mp.setScale(0f)
                mp.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0)
            }
        }

    private fun eventListener(mp: MediaPlayer, gen: Int) =
        MediaPlayer.EventListener { event -> onEvent(mp, gen, event) }

    private fun onEvent(mp: MediaPlayer, gen: Int, event: MediaPlayer.Event) {
        // The old stream's events still arrive after a zap or release() posted them.
        if (gen != generation) return
        when (event.type) {
            MediaPlayer.Event.TimeChanged ->
                stateFlow.update { it.copy(timeMs = event.timeChanged) }

            MediaPlayer.Event.PositionChanged ->
                stateFlow.update { it.copy(position = event.positionChanged) }

            MediaPlayer.Event.LengthChanged ->
                stateFlow.update { it.copy(lengthMs = event.lengthChanged) }

            MediaPlayer.Event.SeekableChanged ->
                stateFlow.update { it.copy(seekable = event.seekable) }

            MediaPlayer.Event.Playing,
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted -> refreshTrackCounts(mp)
        }
        listener?.onEvent(event)
    }

    private fun refreshTrackCounts(mp: MediaPlayer) {
        if (switching || !mp.hasMedia()) return
        val audio = mp.audioTracksCount
        val subtitles = mp.spuTracksCount
        stateFlow.update { it.copy(audioTracks = audio, subtitleTracks = subtitles) }
    }

    companion object {
        private const val LOG_TAG = "VLCPlayer"

        const val MEDIA_HWACCEL_ENABLED = 0x01
        const val MEDIA_HWACCEL_FORCE = 0x02
    }
}
