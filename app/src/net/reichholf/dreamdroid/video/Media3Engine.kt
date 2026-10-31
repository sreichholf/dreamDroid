package net.reichholf.dreamdroid.video

import android.content.Context
import android.util.Log
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import net.reichholf.dreamdroid.BuildConfig
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.di.ApplicationScope
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.helpers.HttpUserInfo

/**
 * Media3 (ExoPlayer) on the device's decoders. Each [start] builds a new [ExoPlayer], so the
 * buffer setting and the profile's TLS setup apply per stream. Reads the stream through the
 * profile's OkHttp client; the stream login travels as a header, since ExoPlayer ignores URL
 * credentials.
 */
@OptIn(UnstableApi::class)
@Singleton
class Media3Engine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val profiles: ProfileRepository,
    private val okHttp: EnigmaOkHttp
) : PlayerEngine {
    private var player: ExoPlayer? = null
    private var views: PlayerViews? = null
    private var listener: ((PlayerEvent) -> Unit)? = null
    private var progressJob: Job? = null
    private var playerListener: PlayerListener? = null
    private var preferredAudioLanguage = ""
    private var preferredAudioTrackSelected = false

    private val stateFlow = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = stateFlow.asStateFlow()

    override fun setEventListener(listener: ((PlayerEvent) -> Unit)?) {
        this.listener = listener
    }

    /** [PlayerViews.media3Frame] keeps the surface at the video's aspect ratio. */
    override fun attach(views: PlayerViews) {
        this.views = views
        views.media3Frame.visibility = View.VISIBLE
        player?.setVideoSurfaceView(views.media3Surface)
    }

    override fun detach() {
        views?.let { unbind(it) }
        views = null
        releasePlayer()
    }

    override fun release() {
        views?.let { unbind(it) }
        views = null
        releasePlayer()
        stateFlow.value = PlayerState()
    }

    override fun start(request: PlayRequest) {
        releasePlayer()
        stateFlow.value = PlayerState()
        preferredAudioLanguage = request.preferredAudioLanguage
        preferredAudioTrackSelected = false
        val (url, authorization) = HttpUserInfo.toBasicAuth(request.url)
        val trustAll = profiles.current.value?.allCertsTrusted == true
        val dataSource = OkHttpDataSource.Factory(okHttp.client(STREAM_TIMEOUT_MS, trustAll))
        authorization?.let {
            dataSource.setDefaultRequestProperties(mapOf("Authorization" to it))
        }
        val source = ProgressiveMediaSource.Factory(dataSource, Media3Streams.dvbExtractors())
            .createMediaSource(MediaItem.fromUri(url))
        val exo = ExoPlayer.Builder(
            context,
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        )
            .setLoadControl(loadControl(request.bufferMs))
            .build()
        val playerListener = PlayerListener(exo, VideoPlayback.noVideoTimeoutMs(request.bufferMs))
        exo.addListener(playerListener)
        this.playerListener = playerListener
        // Decoder names, formats, first frame and dropped frames in logcat, for field reports.
        if (BuildConfig.DEBUG) exo.addAnalyticsListener(EventLogger())
        views?.let { exo.setVideoSurfaceView(it.media3Surface) }
        exo.setMediaSource(source)
        exo.playWhenReady = true
        exo.prepare()
        player = exo
    }

    override fun play() {
        val exo = player ?: return
        exo.playWhenReady = !exo.playWhenReady
    }

    override fun setPosition(position: Float) {
        val exo = player ?: return
        val duration = exo.duration
        if (duration == C.TIME_UNSET || !exo.isCurrentMediaItemSeekable) return
        stateFlow.update { it.copy(position = position) }
        exo.seekTo((duration * position).toLong())
    }

    /** ExoPlayer cannot play backwards; steps back instead. */
    override fun slower() {
        val exo = player ?: return
        if (!exo.isCurrentMediaItemSeekable) return
        exo.seekTo(max(0L, exo.currentPosition - REWIND_STEP_MS))
    }

    override fun audioTracks(): List<MediaTrack>? =
        reportingTracks()?.let { tracksOf(it, C.TRACK_TYPE_AUDIO) }

    override fun subtitleTracks(): List<MediaTrack>? {
        val exo = reportingTracks() ?: return null
        val off = MediaTrack(TRACK_OFF, context.getString(R.string.track_off))
        return listOf(off) + tracksOf(exo, C.TRACK_TYPE_TEXT)
    }

    override fun setAudioTrack(id: Int) = select(C.TRACK_TYPE_AUDIO, id)

    override fun setSubtitleTrack(id: Int) = select(C.TRACK_TYPE_TEXT, id)

    /** The player once its stream has reported tracks. */
    private fun reportingTracks(): ExoPlayer? = player?.takeUnless { it.currentTracks.isEmpty }

    private fun select(type: Int, id: Int) {
        val exo = player ?: return
        select(exo, type, id)
    }

    private fun select(exo: ExoPlayer, type: Int, id: Int) {
        val builder = exo.trackSelectionParameters.buildUpon()
        if (id == TRACK_OFF) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            val group = exo.currentTracks.groups.getOrNull(id shr TRACK_SHIFT) ?: return
            builder.setTrackTypeDisabled(type, false)
                .setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, id and TRACK_MASK)
                )
        }
        exo.trackSelectionParameters = builder.build()
    }

    /** The tracks of [type]; an id holds the group index and the track index in it. */
    private fun tracksOf(exo: ExoPlayer, type: Int): List<MediaTrack> =
        exo.currentTracks.groups.withIndex()
            .filter { (_, group) -> group.type == type }
            .flatMap { (groupIndex, group) ->
                (0 until group.length).filter(group::isTrackSupported).map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    val name = listOfNotNull(
                        format.label
                            ?: format.language?.let { Locale.forLanguageTag(it).displayLanguage },
                        format.sampleMimeType?.substringAfter('/')?.uppercase()
                    ).joinToString(" · ")
                    MediaTrack((groupIndex shl TRACK_SHIFT) or trackIndex, name)
                }
            }

    /** The audio [tracksOf] with each track's language code, for [preferredAudioTrackId]. */
    private fun audioTrackOptions(exo: ExoPlayer): List<AudioTrackOption> {
        val groups = exo.currentTracks.groups
        return tracksOf(exo, C.TRACK_TYPE_AUDIO).map { track ->
            val format = groups[track.id shr TRACK_SHIFT].getTrackFormat(track.id and TRACK_MASK)
            AudioTrackOption(track.id, track.name, format.language)
        }
    }

    private fun unbind(views: PlayerViews) {
        player?.clearVideoSurfaceView(views.media3Surface)
        views.subtitleView.setCues(emptyList())
        views.media3Frame.visibility = View.GONE
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        playerListener?.firstPicture?.cancel()
        playerListener = null
        val exo = player ?: return
        // Its listener drops what release() still reports.
        player = null
        exo.release()
    }

    private fun loadControl(bufferMs: Int) = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
            bufferMs,
            max(bufferMs, DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        )
        .build()

    /** ExoPlayer reports time only when asked; polls it while the stream plays. */
    private fun pollProgress(exo: ExoPlayer) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            var last = -1L
            while (isActive && player === exo) {
                val time = exo.currentPosition
                val duration = exo.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                stateFlow.update {
                    it.copy(
                        timeMs = time,
                        lengthMs = duration,
                        position = if (duration > 0) time.toFloat() / duration else 0f,
                        seekable = exo.isCurrentMediaItemSeekable
                    )
                }
                if (time != last) {
                    last = time
                    listener?.invoke(PlayerEvent.Progress)
                }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private inner class PlayerListener(
        private val exo: ExoPlayer,
        private val noVideoTimeoutMs: Long
    ) : Player.Listener {
        /** Opening and Playing go out once per stream, as VLC sends them; not per rebuffer. */
        private var started = false
        private var tracksFound = false
        val firstPicture = FirstPictureWatch(
            scope + Dispatchers.Main.immediate,
            noVideoTimeoutMs
        ) { event ->
            if (event == PlayerEvent.NoVideo) {
                Log.i(LOG_TAG, "Video decoder showed no frame in $noVideoTimeoutMs ms")
            }
            emit(event)
        }

        private fun emit(event: PlayerEvent) {
            if (player === exo) listener?.invoke(event)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> if (!started) emit(PlayerEvent.Opening)

                Player.STATE_READY -> if (!started) {
                    started = true
                    emit(PlayerEvent.Playing)
                }

                Player.STATE_ENDED -> emit(PlayerEvent.Ended)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && player === exo) pollProgress(exo) else progressJob?.cancel()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(LOG_TAG, "Playback failed: ${error.errorCodeName}", error)
            emit(PlayerEvent.Failed(unsupported = Media3Streams.isUnsupported(error.errorCode)))
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (player !== exo) return
            if (!tracksFound &&
                tracks.groups.any {
                    it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_VIDEO
                }
            ) {
                tracksFound = true
                emit(PlayerEvent.TracksFound)
            }
            stateFlow.update {
                it.copy(
                    audioTracks = tracksOf(exo, C.TRACK_TYPE_AUDIO).size,
                    subtitleTracks = tracksOf(exo, C.TRACK_TYPE_TEXT).size
                )
            }
            if (!preferredAudioTrackSelected && preferredAudioLanguage.isNotBlank()) {
                val preferredTrack = preferredAudioTrackId(
                    preferredAudioLanguage,
                    audioTrackOptions(exo)
                )
                if (preferredTrack != null) {
                    preferredAudioTrackSelected = true
                    select(exo, C.TRACK_TYPE_AUDIO, preferredTrack)
                }
            }
            val support = tracks.groups.map {
                Media3Streams.GroupSupport(it.type, it.isSupported)
            }
            if (Media3Streams.missingDecoder(support)) {
                emit(PlayerEvent.Failed(unsupported = true))
            } else {
                firstPicture.onTracks(hasVideo = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO))
            }
        }

        override fun onRenderedFirstFrame() {
            if (player === exo) firstPicture.onFirstFrame()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (player !== exo || videoSize.width == 0 || videoSize.height == 0) return
            views?.media3Frame?.setAspectRatio(
                videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            )
            val sarDen = SAR_SCALE
            val sarNum = (videoSize.pixelWidthHeightRatio * sarDen).toInt()
            emit(
                PlayerEvent.Layout(
                    VideoLayout(
                        videoSize.width,
                        videoSize.height,
                        videoSize.width,
                        videoSize.height,
                        sarNum,
                        sarDen
                    )
                )
            )
        }

        override fun onCues(cueGroup: CueGroup) {
            if (player === exo) views?.subtitleView?.setCues(cueGroup.cues)
        }
    }

    private companion object {
        const val LOG_TAG = "Media3Engine"

        /** Generous: after a zap the receiver may send nothing while it tunes. */
        const val STREAM_TIMEOUT_MS = 15_000
        const val PROGRESS_INTERVAL_MS = 500L
        const val REWIND_STEP_MS = 10_000L
        const val SAR_SCALE = 1000
        const val TRACK_OFF = -1
        const val TRACK_SHIFT = 16
        const val TRACK_MASK = 0xFFFF
    }
}
