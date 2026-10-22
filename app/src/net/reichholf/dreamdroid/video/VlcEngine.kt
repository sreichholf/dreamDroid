package net.reichholf.dreamdroid.video

import android.content.Context
import android.util.Log
import android.view.View
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.di.ApplicationScope
import net.reichholf.dreamdroid.helpers.HttpUserInfo
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

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

    /** How the next stream's first picture is awaited; set by [start], used on Playing. */
    private var videoWatch: VideoWatch? = null
    private var videoWatchJob: Job? = null

    /** True once the stream [start] began reported an audio or video track. */
    private var tracksFound = false
    private var preferredAudioLanguage = ""
    private var preferredAudioTrackGeneration: Int = -1
    private var views: PlayerViews? = null

    private val stateFlow = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = stateFlow.asStateFlow()

    override fun setEventListener(listener: ((PlayerEvent) -> Unit)?) {
        this.listener = listener
    }

    /** libVLC sizes and places the picture inside [PlayerViews.vlcLayout] itself. */
    override fun attach(views: PlayerViews) {
        this.views = views
        val mp = mediaPlayer()
        if (mp.vlcVout.areViewsAttached()) {
            mp.detachViews()
        }
        views.vlcLayout.visibility = View.VISIBLE
        mp.attachViews(views.vlcLayout, null, true, false)
        mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
    }

    override fun detach() {
        val mp = mediaPlayer ?: return
        if (!VideoPlayback.shouldDetachViews(true, mp.vlcVout.areViewsAttached())) {
            return
        }
        generation++
        mp.detachViews()
        hideViews()
        commands.post { mp.stop() }
    }

    override fun release() {
        hideViews()
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        generation++
        mp.setEventListener(null)
        if (mp.vlcVout.areViewsAttached()) {
            mp.detachViews()
        }
        commands.post {
            mp.stop()
            mp.release()
        }
        stateFlow.value = PlayerState()
    }

    private fun hideViews() {
        views?.vlcLayout?.visibility = View.GONE
        views = null
    }

    override fun start(request: PlayRequest) {
        val mp = mediaPlayer()
        val gen = ++generation
        stateFlow.value = PlayerState()
        tracksFound = false
        preferredAudioLanguage = request.preferredAudioLanguage
        val isHwAccel = request.hwAccelFlags and MEDIA_HWACCEL_ENABLED > 0
        val isHwAccelForce = request.hwAccelFlags and MEDIA_HWACCEL_FORCE > 0
        videoWatchJob?.cancel()
        videoWatch = VideoWatch(
            timeoutMs = VideoPlayback.noVideoTimeoutMs(request.bufferMs),
            // Only a hardware decoder VLC may leave falls back; software has nowhere to go.
            reportNoVideo = isHwAccel && !isHwAccelForce
        )
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
            commands.post { mp.setVideoTitleDisplay(MediaPlayer.Position.Disable, 0) }
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
                selectPreferredAudioTrack(mp, gen)
                watchForVideo(mp, gen)
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

            MediaPlayer.Event.Vout -> {
                if (event.voutCount > 0) emitVideoLayout(mp, gen)
                null
            }

            MediaPlayer.Event.ESAdded -> {
                refreshTrackCounts(mp)
                selectPreferredAudioTrack(mp, gen)
                val type = event.esChangedType
                if (!tracksFound &&
                    (type == IMedia.Track.Type.Audio || type == IMedia.Track.Type.Video)
                ) {
                    tracksFound = true
                    PlayerEvent.TracksFound
                } else {
                    null
                }
            }

            MediaPlayer.Event.ESDeleted -> {
                refreshTrackCounts(mp)
                selectPreferredAudioTrack(mp, gen)
                null
            }

            MediaPlayer.Event.EncounteredError -> PlayerEvent.Failed()

            MediaPlayer.Event.EndReached -> PlayerEvent.Ended

            else -> null
        }
        forwarded?.let { listener?.invoke(it) }
    }

    /**
     * Once per stream, polls libVLC's picture counts: [PlayerEvent.PictureReady] when the first
     * picture is decoded or shown, or when the stream turns out to have no video;
     * [PlayerEvent.NoVideo] when a hardware decoder has shown nothing in time.
     */
    private fun watchForVideo(mp: MediaPlayer, gen: Int) {
        val watch = videoWatch ?: return
        videoWatch = null
        videoWatchJob = scope.launch(Dispatchers.Main.immediate) {
            var waitedMs = 0L
            while (waitedMs < MAX_PICTURE_WAIT_MS) {
                delay(PICTURE_POLL_MS)
                waitedMs += PICTURE_POLL_MS
                if (gen != generation) return@launch
                val (hasVideo, shown) = commands.call {
                    val media = mp.media
                    try {
                        val stats = media?.stats
                        val shown = stats != null &&
                            (stats.decodedVideo > 0 || stats.displayedPictures > 0)
                        (mp.videoTracksCount > 0) to shown
                    } finally {
                        media?.release()
                    }
                }
                if (gen != generation) return@launch
                val event = when {
                    shown -> PlayerEvent.PictureReady
                    waitedMs < watch.timeoutMs -> null
                    !hasVideo -> PlayerEvent.PictureReady
                    watch.reportNoVideo -> PlayerEvent.NoVideo
                    else -> null
                }
                if (event != null) {
                    if (event == PlayerEvent.NoVideo) {
                        Log.i(LOG_TAG, "Hardware decoder showed no video in $waitedMs ms")
                    }
                    listener?.invoke(event)
                    return@launch
                }
            }
        }
    }

    private fun emitVideoLayout(mp: MediaPlayer, gen: Int) {
        scope.launch(Dispatchers.Main.immediate) {
            val track = commands.call { mp.currentVideoTrack } ?: return@launch
            if (gen != generation || track.width == 0 || track.height == 0) return@launch
            listener?.invoke(
                PlayerEvent.Layout(
                    VideoLayout(
                        track.width,
                        track.height,
                        track.width,
                        track.height,
                        track.sarNum,
                        track.sarDen
                    )
                )
            )
        }
    }

    private fun refreshTrackCounts(mp: MediaPlayer) {
        if (switching || !mp.hasMedia()) return
        val audio = mp.audioTracksCount
        val subtitles = mp.spuTracksCount
        stateFlow.update { it.copy(audioTracks = audio, subtitleTracks = subtitles) }
    }

    private fun selectPreferredAudioTrack(mp: MediaPlayer, gen: Int) {
        if (preferredAudioTrackGeneration == gen || gen != generation) return
        val language = preferredAudioLanguage
        if (language.isBlank()) return
        if (switching || !mp.hasMedia()) return
        val tracks = mp.audioTracks ?: return
        val languages = audioLanguages(mp)
        val trackId = preferredAudioTrackId(
            language,
            tracks.map { AudioTrackOption(it.id, it.name.orEmpty(), languages[it.id]) }
        ) ?: return
        preferredAudioTrackGeneration = gen
        if (mp.audioTrack != trackId) {
            commands.post {
                if (gen == generation) mp.setAudioTrack(trackId)
            }
        }
    }

    /**
     * The language code of each audio ES, by the id [MediaPlayer.setAudioTrack] takes. The
     * media keeps the first list it reads, so ESs added later have none and match by name.
     */
    private fun audioLanguages(mp: MediaPlayer): Map<Int, String> {
        val media = mp.media ?: return emptyMap()
        return try {
            (0 until media.trackCount)
                .mapNotNull { media.getTrack(it) }
                .filter { it.type == IMedia.Track.Type.Audio && !it.language.isNullOrBlank() }
                .associate { it.id to it.language }
        } finally {
            media.release()
        }
    }

    companion object {
        private const val LOG_TAG = "VlcEngine"

        private const val PICTURE_POLL_MS = 250L

        /** Stops watching a stream that shows no picture, so the poll does not run forever. */
        private const val MAX_PICTURE_WAIT_MS = 120_000L

        const val MEDIA_HWACCEL_ENABLED = 0x01
        const val MEDIA_HWACCEL_FORCE = 0x02
    }
}

/** [timeoutMs] after Playing, a stream with video should show a picture. */
private class VideoWatch(val timeoutMs: Long, val reportNoVideo: Boolean)
