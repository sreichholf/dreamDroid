package net.reichholf.dreamdroid.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [VideoSessionPlayer] is a Media3 [Player] bound to the main looper, so it is tested on a
 * device: what a `MediaSession` shows of the stream, and what it forwards to [VideoPlayer].
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class VideoSessionPlayerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val log = mutableListOf<String>()
    private val vlc = SessionEngine(log)
    private val media3 = SessionEngine(log)
    private lateinit var scope: CoroutineScope
    private lateinit var player: VideoPlayer
    private lateinit var session: VideoSessionPlayer

    @Before
    fun setUp() {
        onMain {
            scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
            player = VideoPlayer(vlc, media3, scope)
            session = VideoSessionPlayer(
                player,
                onStop = { log += "stop" },
                onUserPlayPause = { log += "userPlayPause" }
            )
        }
        settle()
    }

    @After
    fun tearDown() {
        onMain {
            session.close()
            scope.cancel()
        }
    }

    @Test
    fun aSessionWithoutAStreamShowsBufferingWithoutDurationOrSeek() {
        val commands = onMain { session.availableCommands }

        assertEquals(Player.STATE_BUFFERING, onMain { session.playbackState })
        assertEquals(C.TIME_UNSET, onMain { session.duration })
        assertEquals(0L, onMain { session.contentPosition })
        assertFalse(onMain { session.playWhenReady })
        assertTrue(commands.contains(Player.COMMAND_PLAY_PAUSE))
        assertTrue(commands.contains(Player.COMMAND_STOP))
        assertTrue(commands.contains(Player.COMMAND_GET_METADATA))
        assertTrue(commands.contains(Player.COMMAND_GET_TIMELINE))
        assertFalse(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))

        onMain { session.seekTo(30_000) }

        assertEquals(emptyList<Float>(), vlc.positions)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun everyPlayAndPauseTheSessionAsksForReachesThePlayerAndTheAudioFocus() {
        onMain { player.playUri(recording()) }

        onMain { session.setPlayWhenReady(true) }
        onMain { session.setPlayWhenReady(false) }

        assertEquals(listOf("userPlayPause", "play", "userPlayPause", "pause"), log)
    }

    @Test
    fun aPlayingEventTurnsTheSessionReady() {
        onMain { session.onEvent(PlayerEvent.Playing) }

        assertEquals(Player.STATE_READY, onMain { session.playbackState })
    }

    @Test
    fun progressAlsoCountsAsPlaying() {
        onMain { session.onEvent(PlayerEvent.Progress) }

        assertEquals(Player.STATE_READY, onMain { session.playbackState })
    }

    @Test
    fun openingTurnsTheSessionBackToBuffering() {
        onMain { session.onEvent(PlayerEvent.Playing) }
        onMain { session.onEvent(PlayerEvent.Opening) }

        assertEquals(Player.STATE_BUFFERING, onMain { session.playbackState })
    }

    @Test
    fun theEndOfTheStreamTurnsTheSessionEnded() {
        onMain { session.onEvent(PlayerEvent.Ended) }

        assertEquals(Player.STATE_ENDED, onMain { session.playbackState })
    }

    @Test
    fun anErrorKeepsThePhaseTheStreamHad() {
        onMain { session.onEvent(PlayerEvent.Playing) }
        onMain { session.onEvent(PlayerEvent.Failed()) }
        onMain { session.onEvent(PlayerEvent.NoVideo) }

        assertEquals(Player.STATE_READY, onMain { session.playbackState })
    }

    @Test
    fun theTitleReachesTheNowPlayingMetadata() {
        onMain { session.setTitle("Das Erste") }

        assertEquals("Das Erste", onMain { session.mediaMetadata.title?.toString() })
    }

    @Test
    fun aSeekableRecordingShowsItsDurationPositionAndSeek() {
        engineState(playing = true, seekable = true, lengthMs = 60_000, timeMs = 5_000)

        val commands = onMain { session.availableCommands }

        assertTrue(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(onMain { session.playWhenReady })
        assertEquals(60_000L, onMain { session.duration })
        assertEquals(5_000L, onMain { session.contentPosition })
    }

    @Test
    fun aSeekReachesThePlayerAsAFractionOfTheStream() {
        engineState(seekable = true, lengthMs = 60_000)

        onMain { session.seekTo(30_000) }

        assertEquals(listOf(0.5f), vlc.positions)
    }

    @Test
    fun aSeekPastEitherEndIsClampedToTheStream() {
        engineState(seekable = true, lengthMs = 60_000)

        onMain { session.seekTo(-5_000) }
        onMain { session.seekTo(90_000) }

        assertEquals(listOf(0f, 1f), vlc.positions)
    }

    @Test
    fun aSeekableStreamWithoutALengthIsNotSeeked() {
        engineState(seekable = true, lengthMs = 0)

        val commands = onMain { session.availableCommands }
        onMain { session.seekTo(30_000) }

        assertFalse(commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertEquals(emptyList<Float>(), vlc.positions)
    }

    @Test
    fun stopReachesOnlyTheCloseCallback() {
        onMain { player.playUri(recording()) }

        onMain { session.stop() }

        assertEquals(listOf("stop"), log)
    }

    @Test
    fun theSessionFollowsThePlayerUntilItIsClosed() {
        engineState(playing = true, seekable = true, lengthMs = 60_000, timeMs = 5_000)
        onMain { session.start(scope) }
        settle()
        assertEquals(60_000L, onMain { session.duration })

        engineState(playing = true, seekable = true, lengthMs = 90_000, timeMs = 7_000)
        assertEquals(90_000L, onMain { session.duration })
        assertEquals(7_000L, onMain { session.contentPosition })

        onMain { session.close() }
        engineState(playing = true, seekable = true, lengthMs = 120_000, timeMs = 9_000)

        assertEquals(90_000L, onMain { session.duration })
    }

    /** Runs [block] on the main thread, where [VideoSessionPlayer]'s Media3 state lives. */
    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    /** Lets the main looper run what the state flows scheduled on it. */
    private fun settle() = instrumentation.waitForIdleSync()

    private fun engineState(
        playing: Boolean = false,
        seekable: Boolean = false,
        lengthMs: Long = 0,
        timeMs: Long = 0
    ) {
        onMain {
            vlc.stateFlow.value = PlayerState(
                isPlaying = playing,
                seekable = seekable,
                lengthMs = lengthMs,
                timeMs = timeMs
            )
        }
        settle()
    }

    private fun recording() = PlayRequest(
        url = "http://box:8001/recording.ts",
        engine = Engine.Vlc,
        hwAccelFlags = 0,
        bufferMs = 1_000,
        live = false
    )
}

/** A [PlayerEngine] that records what the player asks of it. */
private class SessionEngine(private val log: MutableList<String>) : PlayerEngine {
    val stateFlow = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = stateFlow
    val positions = mutableListOf<Float>()

    override fun setEventListener(listener: ((PlayerEvent) -> Unit)?) = Unit

    override fun attach(views: PlayerViews) = Unit

    override fun detach() = Unit

    override fun release() = Unit

    override fun start(request: PlayRequest) {
        stateFlow.value = PlayerState(isPlaying = true)
    }

    override fun play() {
        log += "play"
        stateFlow.value = stateFlow.value.copy(isPlaying = true, rewinding = false)
    }

    override fun pause() {
        log += "pause"
        stateFlow.value = stateFlow.value.copy(isPlaying = false)
    }

    override fun setPosition(position: Float) {
        positions += position
    }

    override fun slower() = Unit

    override fun audioTracks(): List<MediaTrack>? = null

    override fun subtitleTracks(): List<MediaTrack>? = null

    override fun setAudioTrack(id: Int) = Unit

    override fun setSubtitleTrack(id: Int) = Unit
}
