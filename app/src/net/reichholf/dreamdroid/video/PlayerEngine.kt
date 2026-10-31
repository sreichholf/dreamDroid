package net.reichholf.dreamdroid.video

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.flow.StateFlow
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * What the overlay shows of the playing stream, kept from the engine's events.
 * [isPlaying]: the stream is meant to play, buffering included; false once paused, ended or
 * failed. [rewinding]: [PlayerEngine.slower] plays it backwards; [PlayerEngine.play] stops that.
 */
data class PlayerState(
    val isPlaying: Boolean = false,
    val rewinding: Boolean = false,
    val seekable: Boolean = false,
    val lengthMs: Long = 0,
    val timeMs: Long = 0,
    val position: Float = 0f,
    val audioTracks: Int = 0,
    val subtitleTracks: Int = 0
)

/**
 * A stream for [VideoPlayer.playUri]. [hwAccelFlags] are [VlcEngine.MEDIA_HWACCEL_ENABLED] and
 * [VlcEngine.MEDIA_HWACCEL_FORCE]; [bufferMs] is how much the engine buffers before it plays.
 * A [live] stream is opened again when it fails or ends (see [LiveReconnect]).
 */
data class PlayRequest(
    val url: String,
    val engine: Engine,
    val hwAccelFlags: Int,
    val bufferMs: Int,
    val live: Boolean,
    val preferredAudioLanguage: String = ""
)

/** The decoder library that plays a stream in the integrated player. */
enum class Engine(val prefValue: String) {
    /** libVLC: decodes in software what the device cannot, MPEG-2 and AC3 included. */
    Vlc("vlc"),

    /** Media3 (ExoPlayer): the device's decoders, with VLC as the fallback. */
    Media3("media3");

    companion object {
        fun fromPref(value: String?): Engine = entries.firstOrNull { it.prefValue == value } ?: Vlc
    }
}

/** What the overlay and the activity hear from the playing engine, on the main thread. */
sealed interface PlayerEvent {
    data object Opening : PlayerEvent

    data object Playing : PlayerEvent

    /** The stream's first audio or video track is known: the receiver sends the service. */
    data object TracksFound : PlayerEvent

    /** Playback time moved: the stream is playing. */
    data object Progress : PlayerEvent

    /** The first video frame is on screen, or the stream turned out to have no video. */
    data object PictureReady : PlayerEvent

    data class Layout(val layout: VideoLayout) : PlayerEvent

    /** [unsupported]: the engine has no decoder for the stream, another engine may have one. */
    data class Failed(val unsupported: Boolean = false) : PlayerEvent

    data object Ended : PlayerEvent

    /** Media3 could not decode the stream, VLC plays it now. */
    data object FellBackToVlc : PlayerEvent

    /**
     * The stream has video, but the hardware decoder showed no frame of it in time (see
     * [VideoPlayback.noVideoTimeoutMs]). Some decoders take a DVB stream and output nothing.
     */
    data object NoVideo : PlayerEvent

    /** The hardware decoder showed no picture; VLC decodes the stream in software now, and
     * every later stream of this session starts there. */
    data object SoftwareDecoding : PlayerEvent
}

/** Video size in pixels; the sample aspect ratio is [sarNum]:[sarDen]. */
data class VideoLayout(
    val width: Int,
    val height: Int,
    val visibleWidth: Int,
    val visibleHeight: Int,
    val sarNum: Int,
    val sarDen: Int
)

/** An audio or subtitle track as the track dialog offers it. */
data class MediaTrack(val id: Int, val name: String)

/**
 * Each engine's own video view; the playing engine shows its view and hides it when released.
 * VLC fits its picture into [vlcLayout] itself; Media3 draws into [media3Surface], which
 * [media3Frame] keeps at the video's aspect ratio, with subtitles in [subtitleView].
 */
@OptIn(UnstableApi::class)
class PlayerViews(
    val vlcLayout: VLCVideoLayout,
    val media3Frame: AspectRatioFrameLayout,
    val media3Surface: SurfaceView,
    val subtitleView: SubtitleView
)

/**
 * One decoder library behind [VideoPlayer]. Call it from the main thread; events reach the
 * listener there too.
 */
interface PlayerEngine {
    val state: StateFlow<PlayerState>

    fun setEventListener(listener: ((PlayerEvent) -> Unit)?)

    fun attach(views: PlayerViews)

    /** Detaches the views and stops the stream. */
    fun detach()

    /** Stops the stream and frees the decoder; the next [start] sets up a new one. */
    fun release()

    fun start(request: PlayRequest)

    /** Plays the stream at normal speed; does nothing to one already playing at it. */
    fun play()

    fun pause()

    fun setPosition(position: Float)

    /** Rewinds a seekable stream. */
    fun slower()

    /** Null while no stream plays or one is still starting. */
    fun audioTracks(): List<MediaTrack>?

    /** Null while no stream plays or one is still starting. */
    fun subtitleTracks(): List<MediaTrack>?

    fun setAudioTrack(id: Int)

    fun setSubtitleTrack(id: Int)
}
