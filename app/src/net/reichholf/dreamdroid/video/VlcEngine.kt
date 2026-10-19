package net.reichholf.dreamdroid.video

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
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
import net.reichholf.dreamdroid.helpers.HttpUserInfo
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout

/**
 * The process's libVLC [MediaPlayer].
 *
 * Calls that can wait for a stream to close (setMedia, stop, release, seeking, track and
 * scale changes) go through [commands]. As in VLC for Android, play(), pause, and the track
 * getters run on the main thread, but only once setMedia has returned (see [switching]).
 *
 * One wait is left, as in VLC for Android: detaching the views while a stream is still
 * closing makes libVLC disable the video track on the main thread, and that waits too.
 * Leaving the player in the middle of a stuck zap can still be slow.
 */
@Singleton
class VlcEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope
) : PlayerEngine {
    private val commands = PlayerCommandQueue(scope, Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Counts streams on the main thread: [start] begins one, [detach] and [release] end it.
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
    private var listener: ((PlayerEvent) -> Unit)? = null

    private val stateFlow = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = stateFlow.asStateFlow()

    private val layoutListener =
        IVLCVout.OnNewVideoLayoutListener {
                _,
                width,
                height,
                visibleWidth,
                visibleHeight,
                sarNum,
                sarDen
            ->
            listener?.invoke(
                PlayerEvent.Layout(
                    VideoLayout(width, height, visibleWidth, visibleHeight, sarNum, sarDen)
                )
            )
        }

    override fun setEventListener(listener: ((PlayerEvent) -> Unit)?) {
        this.listener = listener
    }

    override fun attach(views: PlayerViews) {
        val vlcVout = mediaPlayer().vlcVout
        if (vlcVout.areViewsAttached()) {
            vlcVout.detachViews()
        }
        vlcVout.setVideoView(views.surface)
        vlcVout.setSubtitlesView(views.subtitlesSurface)
        vlcVout.attachViews(layoutListener)
    }

    override fun detach() {
        val mp = mediaPlayer ?: return
        val vlcVout = mp.vlcVout
        if (!VideoPlayback.shouldDetachViews(true, vlcVout.areViewsAttached())) {
            return
        }
        generation++
        vlcVout.detachViews()
        commands.post { mp.stop() }
    }

    override fun release() {
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

    override fun setWindowSize(width: Int, height: Int) {
        mediaPlayer?.vlcVout?.setWindowSize(width, height)
    }

    override fun resetScale() {
        val mp = mediaPlayer ?: return
        commands.post {
            mp.setAspectRatio(null)
            mp.setScale(0f)
        }
    }

    override fun start(request: PlayRequest) {
        val mp = mediaPlayer()
        val gen = ++generation
        stateFlow.value = PlayerState()
        val isHwAccel = request.hwAccelFlags and MEDIA_HWACCEL_ENABLED > 0
        val isHwAccelForce = request.hwAccelFlags and MEDIA_HWACCEL_FORCE > 0
        // VLC for Android's PlayerController.startPlayback: setMedia off main, then listener
        // and play() on main. setMedia joins the old stream's threads, which can take long.
        scope.launch(Dispatchers.Main.immediate) {
            try {
                commands.call {
                    val media = Media(VLCInstance.get(context), request.url.toUri())
                    try {
                        media.setHWDecoderEnabled(isHwAccel || isHwAccelForce, isHwAccelForce)
                        // DVB streams start anywhere in a long GOP; a bigger buffer rides out
                        // the wait for a keyframe and the receiver's slow start after a zap.
                        media.addOption(":network-caching=${request.bufferMs}")
                        // The old stream's teardown events must not reach the overlay.
                        mp.setEventListener(null)
                        mp.media = media
                    } finally {
                        media.release()
                    }
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Starting ${HttpUserInfo.redact(request.url)} failed", e)
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

    override fun play() {
        val mp = mediaPlayer ?: return
        if (switching || !mp.hasMedia()) return
        if (VideoPlayback.shouldTogglePause(mp.isPlaying, mp.rate)) {
            mp.pause()
        } else {
            mp.play()
        }
        mp.rate = 1.0f
    }

    override fun setPosition(position: Float) {
        val mp = mediaPlayer ?: return
        stateFlow.update { it.copy(position = position) }
        commands.post { mp.position = position }
    }

    /** Rewinds faster each call, down to -64x, while a seekable stream plays. */
    override fun slower() {
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

    override fun audioTracks(): List<MediaTrack>? {
        val mp = mediaPlayer ?: return null
        if (switching || !mp.hasMedia()) return null
        return mp.audioTracks?.map { MediaTrack(it.id, it.name) }
    }

    override fun subtitleTracks(): List<MediaTrack>? {
        val mp = mediaPlayer ?: return null
        if (switching || !mp.hasMedia()) return null
        return mp.spuTracks?.map { MediaTrack(it.id, it.name) }
    }

    override fun setAudioTrack(id: Int) {
        val mp = mediaPlayer ?: return
        commands.post { mp.setAudioTrack(id) }
    }

    override fun setSubtitleTrack(id: Int) {
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
        val forwarded = when (event.type) {
            MediaPlayer.Event.Opening -> PlayerEvent.Opening

            MediaPlayer.Event.Playing -> {
                refreshTrackCounts(mp)
                PlayerEvent.Playing
            }

            MediaPlayer.Event.TimeChanged -> {
                stateFlow.update { it.copy(timeMs = event.timeChanged) }
                PlayerEvent.Progress
            }

            MediaPlayer.Event.PositionChanged -> {
                stateFlow.update { it.copy(position = event.positionChanged) }
                PlayerEvent.Progress
            }

            MediaPlayer.Event.LengthChanged -> {
                stateFlow.update { it.copy(lengthMs = event.lengthChanged) }
                null
            }

            MediaPlayer.Event.SeekableChanged -> {
                stateFlow.update { it.copy(seekable = event.seekable) }
                null
            }

            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted -> {
                refreshTrackCounts(mp)
                null
            }

            MediaPlayer.Event.EncounteredError -> PlayerEvent.Failed()

            MediaPlayer.Event.EndReached -> PlayerEvent.Ended

            else -> null
        }
        forwarded?.let { listener?.invoke(it) }
    }

    private fun refreshTrackCounts(mp: MediaPlayer) {
        if (switching || !mp.hasMedia()) return
        val audio = mp.audioTracksCount
        val subtitles = mp.spuTracksCount
        stateFlow.update { it.copy(audioTracks = audio, subtitleTracks = subtitles) }
    }

    companion object {
        private const val LOG_TAG = "VlcEngine"

        const val MEDIA_HWACCEL_ENABLED = 0x01
        const val MEDIA_HWACCEL_FORCE = 0x02
    }
}
