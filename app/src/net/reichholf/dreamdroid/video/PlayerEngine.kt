package net.reichholf.dreamdroid.video

import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.flow.StateFlow

/** What the overlay shows of the playing stream, kept from the engine's events. */
data class PlayerState(
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
    val live: Boolean
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

    /** Playback time moved: the stream is showing frames. */
    data object Progress : PlayerEvent

    data class Layout(val layout: VideoLayout) : PlayerEvent

    /** [unsupported]: the engine has no decoder for the stream, another engine may have one. */
    data class Failed(val unsupported: Boolean = false) : PlayerEvent

    data object Ended : PlayerEvent

    /** Media3 could not decode the stream, VLC plays it now. */
    data object FellBackToVlc : PlayerEvent
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

/** VLC draws into [surface] and [subtitlesSurface]; Media3 into [surface] and [subtitleView]. */
@OptIn(UnstableApi::class)
class PlayerViews(
    val surface: SurfaceView,
    val subtitlesSurface: SurfaceView,
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

    /** Pauses a stream playing at normal speed; otherwise plays at normal speed. */
    fun play()

    fun setPosition(position: Float)

    /** Rewinds a seekable stream. */
    fun slower()

    fun setWindowSize(width: Int, height: Int)

    /** Lets the engine fit the video to the surface again. */
    fun resetScale()

    /** Null while no stream plays or one is still starting. */
    fun audioTracks(): List<MediaTrack>?

    /** Null while no stream plays or one is still starting. */
    fun subtitleTracks(): List<MediaTrack>?

    fun setAudioTrack(id: Int)

    fun setSubtitleTrack(id: Int)
}
