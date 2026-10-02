package net.reichholf.dreamdroid.video

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * Every [MediaPlayer] call goes through [commands], so a stream that is slow to close never
 * blocks input. Read the stream's time, length, and tracks from [state], not from libVLC.
 */
@Singleton
class VLCPlayer @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val commands = PlayerCommandQueue(Dispatchers.IO)

    // Written on the main thread; read by queued commands to drop a released player's results.
    @Volatile
    private var mediaPlayer: MediaPlayer? = null
    private var mediaPlayerEvents: MediaPlayer.EventListener? = null
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
        vlcVout.detachViews()
        commands.post { mp.stop() }
    }

    /** Frees the player. The next call creates a new one. */
    fun release() {
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        mediaPlayerEvents = null
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

    fun addVoutCallback(callback: IVLCVout.Callback) {
        mediaPlayer().vlcVout.addCallback(callback)
    }

    fun removeVoutCallback(callback: IVLCVout.Callback) {
        mediaPlayer?.vlcVout?.removeCallback(callback)
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

    fun onSurfacesCreated() {
        val mp = mediaPlayer ?: return
        commands.post {
            mp.setAspectRatio(null)
            mp.setScale(0f)
            mp.setVideoTrackEnabled(true)
        }
    }

    fun playUri(uri: Uri, flags: Int) {
        val mp = mediaPlayer()
        val events = mediaPlayerEvents
        stateFlow.value = PlayerState()
        val isHwAccel = flags and MEDIA_HWACCEL_ENABLED > 0
        val isHwAccelForce = flags and MEDIA_HWACCEL_FORCE > 0
        commands.post {
            val media = Media(VLCInstance.get(context), uri)
            media.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce)
            // Like VLC for Android: the old stream's teardown events must not reach the
            // overlay. setMedia joins the old stream's threads, so it must stay off main.
            mp.setEventListener(null)
            mp.media = media
            mp.setEventListener(events)
            media.release()
            mp.rate = 1.0f
            mp.play()
        }
    }

    /** Pauses a stream playing at normal speed; otherwise plays at normal speed. */
    fun play() {
        val mp = mediaPlayer ?: return
        commands.post {
            if (!mp.hasMedia()) return@post
            if (VideoPlayback.shouldTogglePause(mp.isPlaying, mp.rate, sameMedia = true)) {
                mp.pause()
            } else {
                mp.play()
            }
            mp.rate = 1.0f
        }
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

    suspend fun audioTracks(): Array<MediaPlayer.TrackDescription>? {
        val mp = mediaPlayer ?: return null
        return commands.call { mp.audioTracks }
    }

    suspend fun subtitleTracks(): Array<MediaPlayer.TrackDescription>? {
        val mp = mediaPlayer ?: return null
        return commands.call { mp.spuTracks }
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
            val events = MediaPlayer.EventListener { event -> onEvent(mp, event) }
            mediaPlayerEvents = events
            mp.setEventListener(events)
            commands.post {
                mp.setAspectRatio(null)
                mp.setScale(0f)
                mp.setVideoTrackEnabled(true)
                mp.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0)
            }
        }

    private fun onEvent(mp: MediaPlayer, event: MediaPlayer.Event) {
        // Events queued before release() can still arrive.
        if (mp !== mediaPlayer) return
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
        commands.post {
            val audio = mp.audioTracksCount
            val subtitles = mp.spuTracksCount
            if (mp === mediaPlayer) {
                stateFlow.update { it.copy(audioTracks = audio, subtitleTracks = subtitles) }
            }
        }
    }

    companion object {
        const val MEDIA_HWACCEL_DISABLED = 0x00
        const val MEDIA_HWACCEL_ENABLED = 0x01
        const val MEDIA_HWACCEL_FORCE = 0x02
    }
}
