package net.reichholf.dreamdroid.video

import android.view.SurfaceView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.SubtitleView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.testing.HiltComposeTestActivity
import net.reichholf.dreamdroid.testutil.DVB_NON_IDR_STREAM
import net.reichholf.dreamdroid.testutil.DVB_STREAM_HEIGHT
import net.reichholf.dreamdroid.testutil.DVB_STREAM_WIDTH
import net.reichholf.dreamdroid.testutil.loadStreamFixture
import net.reichholf.dreamdroid.testutil.streamServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Both engines start a receiver-like stream that has no IDR frame, through [VideoPlayer] on
 * the real Hilt graph: they reach Playing, play on, and report the video size.
 */
@HiltAndroidTest
class DvbStreamPlaybackTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltComposeTestActivity>()

    @Inject
    lateinit var player: VideoPlayer

    private val server = streamServer(loadStreamFixture(DVB_NON_IDR_STREAM))

    @Before
    fun setUp() {
        hiltRule.inject()
        server.start()
    }

    @After
    fun tearDown() {
        composeRule.runOnUiThread {
            player.setEventListener(null)
            player.release()
        }
        server.shutdown()
    }

    @Test
    fun media3StartsAStreamWithoutIdrFrames() = runBlocking {
        val size = withTimeout(TIMEOUT_MS) { playsOn(Engine.Media3).await() }

        assertEquals(DVB_STREAM_WIDTH, size.width)
        assertEquals(DVB_STREAM_HEIGHT, size.height)
    }

    /**
     * Plays and keeps time only. On the CI emulator image libVLC opens its video output when
     * the stream has ended, whatever the stream; desktop VLC 3.0 starts this file's video at its
     * first SEI recovery point, 0.8 s in.
     */
    @Test
    fun vlcPlaysAStreamWithoutIdrFrames() = runBlocking {
        playsOn(Engine.Vlc)
        Unit
    }

    /**
     * Plays the fixture on [engine] until it reports Playing and its time moves, without an
     * error or a fallback. Returns the video size the engine reports, possibly later.
     */
    private suspend fun playsOn(engine: Engine): CompletableDeferred<VideoLayout> {
        val views = showViews()
        val playing = CompletableDeferred<Unit>()
        val progressed = CompletableDeferred<Unit>()
        val layout = CompletableDeferred<VideoLayout>()
        val trouble = mutableListOf<PlayerEvent>()
        withContext(Dispatchers.Main) {
            player.attach(views)
            player.setWindowSize(views.surface.width, views.surface.height)
            player.setEventListener { event ->
                when (event) {
                    PlayerEvent.Playing -> playing.complete(Unit)
                    PlayerEvent.Progress -> progressed.complete(Unit)
                    is PlayerEvent.Layout -> layout.complete(event.layout)
                    is PlayerEvent.Failed, PlayerEvent.FellBackToVlc -> trouble += event
                    else -> Unit
                }
            }
            player.playUri(
                PlayRequest(
                    url = server.url("/1:0:19:283D:3FB:1:C00000:0:0:0:").toString(),
                    engine = engine,
                    // Software only: VLC's hardware path looks up the OMX codec store, which the
                    // CI image (aosp_atd) lacks, and waits for it forever. The stream handling
                    // under test is the same either way.
                    hwAccelFlags = 0,
                    bufferMs = 1_000,
                    live = false
                )
            )
        }

        withTimeout(TIMEOUT_MS) {
            playing.await()
            progressed.await()
        }
        // The engine played it itself: no fallback to VLC, no error.
        assertEquals(emptyList<PlayerEvent>(), withContext(Dispatchers.Main) { trouble.toList() })
        return layout
    }

    private fun showViews(): PlayerViews {
        var views: PlayerViews? = null
        composeRule.setContent {
            AndroidView(
                factory = { context ->
                    val surface = SurfaceView(context)
                    val subtitlesSurface = SurfaceView(context)
                    val subtitleView = SubtitleView(context)
                    views = PlayerViews(surface, subtitlesSurface, subtitleView)
                    // Full size, as in VideoActivity: VLC shows video only in a window it can size.
                    FrameLayout(context).apply {
                        addView(surface, MATCH_PARENT, MATCH_PARENT)
                        addView(subtitlesSurface, MATCH_PARENT, MATCH_PARENT)
                        addView(subtitleView, MATCH_PARENT, MATCH_PARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) {
            views?.surface?.let { it.width > 0 && it.height > 0 } == true
        }
        return checkNotNull(views)
    }

    private companion object {
        /** Upper bound only; the software emulator decodes slowly. */
        const val TIMEOUT_MS = 60_000L
    }
}
