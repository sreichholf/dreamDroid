package net.reichholf.dreamdroid.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    /** The player's own scope on the test clock; runTest does not wait for its collectors. */
    private val playerScope = CoroutineScope(dispatcher)
    private val vlc = FakeEngine()
    private val media3 = FakeEngine()
    private val events = mutableListOf<PlayerEvent>()
    private lateinit var player: VideoPlayer

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        player = VideoPlayer(vlc, media3, playerScope)
        player.setEventListener { events += it }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun media3PlaysHttpAndVlcPlaysRtsp() {
        player.playUri(request(Engine.Media3))
        assertEquals(listOf(request(Engine.Media3)), media3.started)

        player.playUri(request(Engine.Media3, url = "rtsp://box:554/stream"))
        assertEquals(listOf(request(Engine.Media3, url = "rtsp://box:554/stream")), vlc.started)
        assertEquals(1, media3.released)
    }

    @Test
    fun aStreamMedia3CannotDecodePlaysOnVlc() {
        player.playUri(request(Engine.Media3))

        media3.emit(PlayerEvent.Failed(unsupported = true))

        assertEquals(listOf(request(Engine.Vlc)), vlc.started)
        assertEquals(1, media3.released)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.FellBackToVlc), events)
    }

    @Test
    fun noPictureFromTheHardwareDecoderReplaysTheStreamInSoftware() {
        player.playUri(request(Engine.Vlc))

        vlc.emit(PlayerEvent.NoVideo)

        assertEquals(listOf(request(Engine.Vlc), software()), vlc.started)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.SoftwareDecoding), events)
    }

    @Test
    fun media3WithoutPictureAlsoGoesToVlcInSoftware() {
        player.playUri(request(Engine.Media3))

        media3.emit(PlayerEvent.NoVideo)

        assertEquals(listOf(software()), vlc.started)
        assertEquals(1, media3.released)
    }

    @Test
    fun afterOneStreamShowedNoPictureEveryStreamStartsInSoftware() {
        player.playUri(request(Engine.Vlc))
        vlc.emit(PlayerEvent.NoVideo)

        player.playUri(request(Engine.Media3))
        player.playUri(request(Engine.Vlc, url = OTHER_URL))

        assertEquals(
            listOf(
                request(Engine.Vlc),
                software(),
                software(),
                request(Engine.Vlc, OTHER_URL).copy(hwAccelFlags = 0)
            ),
            vlc.started
        )
        assertEquals(emptyList<PlayRequest>(), media3.started)
    }

    @Test
    fun theStateFollowsThePlayingEngine() = scope.runTest {
        player.playUri(request(Engine.Media3))
        runCurrent()
        media3.stateFlow.value = PlayerState(audioTracks = 1)
        runCurrent()
        assertEquals(1, player.state.value.audioTracks)

        media3.emit(PlayerEvent.Failed(unsupported = true))
        vlc.stateFlow.value = PlayerState(audioTracks = 3)
        runCurrent()

        assertEquals(3, player.state.value.audioTracks)
    }

    @Test
    fun aDroppedLiveStreamReconnectsUntilTheAttemptsRunOut() = scope.runTest {
        player.playUri(request(Engine.Vlc))

        vlc.emit(PlayerEvent.Failed())
        // The same dropout also ends the stream; it costs no second attempt.
        vlc.emit(PlayerEvent.Ended)
        advanceTimeBy(1_001)
        assertEquals(2, vlc.started.size)
        assertTrue(events.isEmpty())

        vlc.emit(PlayerEvent.Ended)
        advanceTimeBy(2_001)
        vlc.emit(PlayerEvent.Ended)
        advanceTimeBy(4_001)
        assertEquals(4, vlc.started.size)

        vlc.emit(PlayerEvent.Ended)
        advanceUntilIdle()
        assertEquals(4, vlc.started.size)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Ended), events)
    }

    @Test
    fun aStreamThatPlayedAgainGetsItsAttemptsBack() = scope.runTest {
        player.playUri(request(Engine.Vlc))
        repeat(3) {
            vlc.emit(PlayerEvent.Failed())
            advanceTimeBy(5_000)
        }

        vlc.emit(PlayerEvent.Progress)
        vlc.emit(PlayerEvent.Failed())
        advanceTimeBy(1_001)

        assertEquals(5, vlc.started.size)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Progress), events)
    }

    @Test
    fun aStreamWithoutTracksIsReopenedAfterThreeSecondsThenTen() = scope.runTest {
        player.playUri(request(Engine.Vlc))

        // 3 s for the tracks, then the first reconnect wait of 1 s.
        advanceTimeBy(4_001)
        assertEquals(2, vlc.started.size)

        advanceTimeBy(9_000)
        assertEquals(2, vlc.started.size)

        // 10 s for the reopened stream's tracks, then the second wait of 2 s.
        advanceTimeBy(3_000)
        assertEquals(3, vlc.started.size)
        assertTrue(events.isEmpty())
    }

    @Test
    fun aStreamWithTracksGetsTheBufferAndFiveSecondsToPlay() = scope.runTest {
        player.playUri(request(Engine.Vlc))
        vlc.emit(PlayerEvent.TracksFound)

        advanceTimeBy(6_999)
        assertEquals(1, vlc.started.size)

        advanceTimeBy(1_002)
        assertEquals(2, vlc.started.size)
    }

    @Test
    fun everyChannelSwitchStartsWithThreeSecondsAgain() = scope.runTest {
        player.playUri(request(Engine.Vlc))
        advanceTimeBy(4_001)

        player.playUri(request(Engine.Vlc, url = OTHER_URL))
        advanceTimeBy(4_001)

        assertEquals(
            listOf(request(Engine.Vlc), request(Engine.Vlc), request(Engine.Vlc, OTHER_URL)),
            vlc.started.take(3)
        )
        assertEquals(request(Engine.Vlc, OTHER_URL), vlc.started.last())
        assertEquals(4, vlc.started.size)
    }

    @Test
    fun aLiveStreamThatNeverStartsFailsOnceTheAttemptsRunOut() = scope.runTest {
        player.playUri(request(Engine.Vlc))

        advanceUntilIdle()

        assertEquals(4, vlc.started.size)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Failed()), events)
    }

    @Test
    fun aPlayingLiveStreamIsLeftAlone() = scope.runTest {
        player.playUri(request(Engine.Vlc))

        vlc.emit(PlayerEvent.Progress)
        advanceUntilIdle()

        assertEquals(1, vlc.started.size)
    }

    @Test
    fun aRecordingEndsWithoutReconnecting() = scope.runTest {
        player.playUri(request(Engine.Vlc, live = false))

        vlc.emit(PlayerEvent.Ended)
        advanceUntilIdle()

        assertEquals(1, vlc.started.size)
        assertEquals(listOf<PlayerEvent>(PlayerEvent.Ended), events)
    }

    @Test
    fun leavingThePlayerCancelsAPendingReconnect() = scope.runTest {
        player.playUri(request(Engine.Vlc))
        vlc.emit(PlayerEvent.Failed())

        player.release()
        advanceUntilIdle()

        assertEquals(1, vlc.started.size)
    }

    private fun software() = request(Engine.Vlc).copy(hwAccelFlags = 0)

    private fun request(
        engine: Engine,
        url: String = "http://box:8001/1:0:19:283D:3FB:1:C00000:0:0:0:",
        live: Boolean = true
    ) = PlayRequest(url, engine, VlcEngine.MEDIA_HWACCEL_ENABLED, 2_000, live)
}

private const val OTHER_URL = "http://box:8001/1:0:19:2B66:3F3:1:C00000:0:0:0:"

private class FakeEngine : PlayerEngine {
    val stateFlow = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = stateFlow
    val started = mutableListOf<PlayRequest>()
    var released = 0
    private var listener: ((PlayerEvent) -> Unit)? = null

    fun emit(event: PlayerEvent) {
        listener?.invoke(event)
    }

    override fun setEventListener(listener: ((PlayerEvent) -> Unit)?) {
        this.listener = listener
    }

    override fun attach(views: PlayerViews) = Unit

    override fun detach() = Unit

    override fun release() {
        released++
    }

    override fun start(request: PlayRequest) {
        started += request
    }

    override fun play() = Unit

    override fun setPosition(position: Float) = Unit

    override fun slower() = Unit

    override fun audioTracks(): List<MediaTrack>? = null

    override fun subtitleTracks(): List<MediaTrack>? = null

    override fun setAudioTrack(id: Int) = Unit

    override fun setSubtitleTrack(id: Int) = Unit
}
