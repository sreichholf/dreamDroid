package net.reichholf.dreamdroid.video

import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.activities.VideoActivity
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

    /** The player's own layout, at full size as in [VideoActivity]. */
    private fun showViews(): PlayerViews {
        var root: View? = null
        composeRule.setContent {
            AndroidView(
                factory = { context ->
                    LayoutInflater.from(context).inflate(R.layout.video_player, null)
                        .also { root = it }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        composeRule.waitUntil(TIMEOUT_MS) {
            root?.findViewById<View>(R.id.player_container)?.let { it.width > 0 } == true
        }
        val layout = checkNotNull(root)
        return PlayerViews(
            vlcLayout = layout.findViewById(R.id.vlc_video_layout),
            media3Frame = layout.findViewById(R.id.media3_frame),
            media3Surface = layout.findViewById(R.id.media3_surface),
            subtitleView = layout.findViewById(R.id.subtitles_view)
        )
    }

    private companion object {
        /** Upper bound only; the software emulator decodes slowly. */
        const val TIMEOUT_MS = 60_000L
    }
}
