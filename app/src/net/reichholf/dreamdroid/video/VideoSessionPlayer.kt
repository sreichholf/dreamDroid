package net.reichholf.dreamdroid.video

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** How far the stream got, from the player's events; [SessionPlayback] maps it. */
enum class StreamPhase {
    Opening,
    Ready,
    Ended
}

/** What a media session shows of [PlayerState]; the position is read live. */
data class SessionPlayback(
    val playWhenReady: Boolean,
    val playbackState: Int,
    val seekable: Boolean,
    val durationMs: Long,
    val title: String
)

fun sessionPlayback(state: PlayerState, phase: StreamPhase, title: String) = SessionPlayback(
    playWhenReady = state.isPlaying,
    playbackState = when (phase) {
        StreamPhase.Opening -> Player.STATE_BUFFERING
        StreamPhase.Ready -> Player.STATE_READY
        StreamPhase.Ended -> Player.STATE_ENDED
    },
    seekable = state.seekable && state.lengthMs > 0,
    durationMs = if (state.lengthMs > 0) state.lengthMs else C.TIME_UNSET,
    title = title
)

/**
 * A Media3 [Player] over [player] for a `MediaSession`: system and Bluetooth media keys, the
 * PiP controls and "Now playing" reach the integrated player through it. [onStop] closes the
 * player; [onUserPlayPause] hears every play or pause the session asks for.
 */
@OptIn(UnstableApi::class)
class VideoSessionPlayer(
    private val player: VideoPlayer,
    private val onStop: () -> Unit,
    private val onUserPlayPause: () -> Unit
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val phase = MutableStateFlow(StreamPhase.Opening)
    private val title = MutableStateFlow("")
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            combine(player.state, phase, title, ::sessionPlayback)
                .distinctUntilChanged()
                .collect { invalidateState() }
        }
    }

    fun onEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.Opening -> phase.value = StreamPhase.Opening
            PlayerEvent.Playing, PlayerEvent.Progress -> phase.value = StreamPhase.Ready
            PlayerEvent.Ended -> phase.value = StreamPhase.Ended
            else -> Unit
        }
    }

    fun setTitle(title: String) {
        this.title.value = title
    }

    /** Stops following [player] and releases this session player. */
    fun close() {
        job?.cancel()
        job = null
        release()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun getState(): State {
        val playback = sessionPlayback(player.state.value, phase.value, title.value)
        val commands = Player.Commands.Builder()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_STOP,
                COMMAND_GET_CURRENT_MEDIA_ITEM,
                COMMAND_GET_METADATA,
                COMMAND_GET_TIMELINE
            )
            .addIf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, playback.seekable)
            .build()
        val item = MediaItemData.Builder(MEDIA_ITEM_UID)
            .setMediaItem(
                MediaItem.Builder()
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(playback.title).build())
                    .build()
            )
            .setDurationUs(
                if (playback.durationMs == C.TIME_UNSET) {
                    C.TIME_UNSET
                } else {
                    playback.durationMs * 1000
                }
            )
            .setIsSeekable(playback.seekable)
            .setIsDynamic(!playback.seekable)
            .build()
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(listOf(item))
            .setPlayWhenReady(
                playback.playWhenReady,
                PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            .setPlaybackState(playback.playbackState)
            .setContentPositionMs { player.state.value.timeMs }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        onUserPlayPause()
        if (playWhenReady) player.play() else player.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        onStop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        val lengthMs = player.state.value.lengthMs
        if (player.state.value.seekable && lengthMs > 0) {
            player.setPosition((positionMs.coerceIn(0, lengthMs).toFloat() / lengthMs))
        }
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val MEDIA_ITEM_UID = "stream"
    }
}
