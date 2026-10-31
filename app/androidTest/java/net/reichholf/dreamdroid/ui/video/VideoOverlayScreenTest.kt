package net.reichholf.dreamdroid.ui.video

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.Metadata
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.tv.ui.TV_PLAYER_DETAIL_BODY_TAG
import net.reichholf.dreamdroid.tv.ui.TV_PLAYER_DETAIL_DIALOG_TAG
import net.reichholf.dreamdroid.ui.epg.EPG_DETAIL_CAPPED_TAG
import net.reichholf.dreamdroid.ui.movies.MOVIE_DETAIL_CAPPED_TAG
import net.reichholf.dreamdroid.ui.movies.MOVIE_DETAIL_UNCAPPED_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.video.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VideoOverlayScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun sampleStateShowsTitleNowNextAndButtons() {
        val state = VideoOverlayUiState().apply {
            title = "Das Erste HD"
            nowStart = "20:15"
            nowTitle = "Tatort"
            nowDuration = "90"
            showNow = true
            nextStart = "21:45"
            nextTitle = "Tagesschau"
            nextDuration = "15"
            hasNext = true
            showPvrControls = true
            progressMax = 100
            progress = 40
            progressEnabled = true
            seekable = true
            showAudioButton = true
            showSubtitleButton = true
            showListButton = true
            showInfoButton = true
        }
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayScreen(
                    state = state,
                    onPlay = {},
                    onRewind = {},
                    onForward = {},
                    onInfo = {},
                    onList = {},
                    onAudio = {},
                    onSubtitle = {},
                    onSleepTimer = {},
                    onSeekChange = {}
                )
            }
        }
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Tatort").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rewind").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Forward").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Info").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Services").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Audio Tracks").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Subtitles").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Now").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Next").assertIsDisplayed()
    }

    @Test
    fun playButtonShowsPauseOnlyWhilePlayingForward() {
        val state = VideoOverlayUiState().apply {
            title = "Tatort: Recording"
            showPvrControls = true
            isPlaying = PlayerState(isPlaying = true).playingForward
        }
        setOverlay(state)
        assertPlayPauseShows("Pause", absent = "Play")

        composeRule.runOnIdle {
            state.isPlaying = PlayerState(isPlaying = true, rewinding = true).playingForward
        }
        assertPlayPauseShows("Play", absent = "Pause")

        composeRule.runOnIdle { state.isPlaying = PlayerState(isPlaying = false).playingForward }
        assertPlayPauseShows("Play", absent = "Pause")
    }

    @Test
    fun liveShowsPlayPauseWithoutSeekButtons() {
        val state = VideoOverlayUiState().apply {
            title = "Das Erste HD"
            isPlaying = true
        }
        setOverlay(state)
        assertPlayPauseShows("Pause", absent = "Play")
        composeRule.onNodeWithContentDescription("Rewind").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Forward").assertDoesNotExist()

        composeRule.runOnIdle { state.isPlaying = false }
        assertPlayPauseShows("Play", absent = "Pause")
    }

    private fun setOverlay(state: VideoOverlayUiState) {
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayScreen(
                    state = state,
                    onPlay = {},
                    onRewind = {},
                    onForward = {},
                    onInfo = {},
                    onList = {},
                    onAudio = {},
                    onSubtitle = {},
                    onSleepTimer = {},
                    onSeekChange = {}
                )
            }
        }
    }

    private fun assertPlayPauseShows(label: String, absent: String) {
        composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(absent).assertDoesNotExist()
    }

    @Test
    fun sleepTimerButtonShowsMinutesLeftAndReportsClicks() {
        val state = VideoOverlayUiState().apply { title = "Das Erste HD" }
        var clicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayScreen(
                    state = state,
                    onPlay = {},
                    onRewind = {},
                    onForward = {},
                    onInfo = {},
                    onList = {},
                    onAudio = {},
                    onSubtitle = {},
                    onSleepTimer = { clicks++ },
                    onSeekChange = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Sleep Timer").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }

        state.sleepTimer = SleepTimer.Running(29)
        composeRule.onNodeWithContentDescription("Sleep Timer: 29 min. left")
            .assertIsDisplayed()
        composeRule.onNodeWithText("29").assertIsDisplayed()
    }

    @Test
    fun sliderDragSeeksOnceOnReleaseAndIgnoresProgressWhileDragging() {
        val state = seekableState(progress = 10)
        val seeks = mutableListOf<Int>()
        setSeekableOverlay(state) { seeks += it }
        val slider = composeRule.onNodeWithContentDescription("Seek")

        slider.performTouchInput {
            down(percentOffset(0.1f, 0.5f))
            for (i in 1..10) {
                moveTo(percentOffset(0.1f + 0.065f * i, 0.5f))
            }
        }
        composeRule.runOnIdle { state.progress = 20 }
        composeRule.waitForIdle()
        assertEquals(emptyList<Int>(), seeks)
        assertTrue(
            "thumb must stay under the finger, was ${slider.progress()}",
            slider.progress() > 60f
        )

        slider.performTouchInput { up() }
        composeRule.runOnIdle {
            assertEquals(1, seeks.size)
            assertTrue("seek ${seeks[0]} should be near the drop at 75", abs(seeks[0] - 75) <= 8)
        }
        val target = seeks[0]

        composeRule.runOnIdle { state.progress = 20 }
        composeRule.waitForIdle()
        assertTrue(
            "thumb must stay on the seek target $target until playback reaches it, " +
                "was ${slider.progress()}",
            abs(slider.progress() - target) <= 1f
        )

        composeRule.runOnIdle { state.progress = target }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.progress = 30 }
        composeRule.waitUntil(timeoutMillis = 5_000) { slider.progress() == 30f }
        composeRule.runOnIdle { assertEquals(1, seeks.size) }
    }

    @Test
    fun sliderReturnsToProgressWhenSeekNeverLands() {
        val state = seekableState(progress = 10)
        setSeekableOverlay(state) {}
        val slider = composeRule.onNodeWithContentDescription("Seek")
        composeRule.mainClock.autoAdvance = false

        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(50f, slider.progress())

        composeRule.mainClock.advanceTimeBy(SEEK_SETTLE_TIMEOUT_MS + 100)
        assertEquals(10f, slider.progress())
    }

    @Test
    fun sliderAccessibilitySetProgressSeeksOnce() {
        val state = seekableState(progress = 10)
        val seeks = mutableListOf<Int>()
        setSeekableOverlay(state) { seeks += it }

        composeRule.onNodeWithContentDescription("Seek")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }

        composeRule.runOnIdle { assertEquals(listOf(50), seeks) }
    }

    @Test
    fun liveTvOverlayKeepsSeeThroughAlpha() {
        assertEquals(0.85f, VideoOverlayController.overlayAlpha, 0.001f)
    }

    @Test
    fun progressContainerUsesSurfaceVariantNotPrimaryContainer() {
        var surfaceVariant = Color.Unspecified
        var primaryContainer = Color.Unspecified
        val state = VideoOverlayUiState().apply {
            title = "ZDF HD"
            showNow = true
            nowStart = "16:10"
            nowTitle = "Die Rosenheim-Cops"
            nowDuration = "50"
            // Live-style chrome: progress and play only (no seek buttons) — the blue nest was most obvious.
            progressMax = 100
            progress = 20
            progressEnabled = true
            seekable = false
            showInfoButton = true
            showListButton = true
        }
        composeRule.setContent {
            DreamDroidTheme {
                surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                primaryContainer = MaterialTheme.colorScheme.primaryContainer
                VideoOverlayScreen(
                    state = state,
                    onPlay = {},
                    onRewind = {},
                    onForward = {},
                    onInfo = {},
                    onList = {},
                    onAudio = {},
                    onSubtitle = {},
                    onSleepTimer = {},
                    onSeekChange = {}
                )
            }
        }
        composeRule.onNodeWithTag(VIDEO_OVERLAY_PROGRESS_CONTAINER_TAG).assertIsDisplayed()
        val bitmap = composeRule
            .onNodeWithTag(VIDEO_OVERLAY_PROGRESS_CONTAINER_TAG)
            .captureToImage()
            .asAndroidBitmap()
        val expected = surfaceVariant.toArgb()
        val wrong = primaryContainer.toArgb()
        // Sample the top padding strip (away from the Slider track/thumb).
        var nearSurface = 0
        var nearPrimary = 0
        var samples = 0
        val yMax = (bitmap.height * 0.15f).toInt().coerceAtLeast(1).coerceAtMost(bitmap.height)
        var y = 0
        while (y < yMax) {
            var x = 0
            while (x < bitmap.width) {
                val px = bitmap.getPixel(x, y)
                if (rgbDistance(px, expected) < rgbDistance(px, wrong)) {
                    nearSurface++
                }
                if (rgbDistance(px, wrong) < 40) {
                    nearPrimary++
                }
                samples++
                x += 3
            }
            y += 1
        }
        assertTrue("expected samples along progress nest padding", samples > 10)
        assertTrue(
            "progress nest should match surfaceVariant, not primaryContainer " +
                "(nearSurface=$nearSurface nearPrimary=$nearPrimary samples=$samples " +
                "surfaceVariant=#${Integer.toHexString(
                    expected
                )} primaryContainer=#${Integer.toHexString(wrong)})",
            nearSurface > samples / 2 && nearPrimary < samples / 4
        )
    }

    @Test
    fun videoOverlayControllerIsKotlinClass() {
        assertNotNull(VideoOverlayController::class.java.getAnnotation(Metadata::class.java))
    }

    @Test
    fun epgInfoOpensComposeSheet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = VideoOverlayUiState().apply {
            title = "Live TV"
            showInfoButton = true
        }
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayModals(state, tv = false)
            }
        }
        composeRule.runOnIdle {
            state.showEpgDetail(
                context,
                Event(
                    title = "Tagesschau",
                    serviceName = "Das Erste HD",
                    description = "News",
                    startReadable = "20:00",
                    durationReadable = "15"
                )
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertIsDisplayed()
        composeRule.onNodeWithTag(EPG_DETAIL_CAPPED_TAG).assertExists()
    }

    @Test
    fun tvOverlayEpgInfoIsADialogClosedByClose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = VideoOverlayUiState().apply {
            title = "Live TV"
            showInfoButton = true
        }
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayModals(state, tv = true)
            }
        }
        composeRule.runOnIdle {
            state.showEpgDetail(
                context,
                Event(
                    title = "Tagesschau",
                    serviceName = "Das Erste HD",
                    description = "News",
                    startReadable = "20:00",
                    durationReadable = "15"
                )
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_DIALOG_TAG).assertExists()
        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_BODY_TAG).assertIsFocused()
        composeRule.onNodeWithTag(EPG_DETAIL_CAPPED_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.close)).performClick()
        composeRule.waitUntil { state.epgDetailContent == null }
        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_DIALOG_TAG).assertDoesNotExist()
    }

    @Test
    fun tvOverlayEpgInfoScrollsAKeyRepeatStepAtATime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = VideoOverlayUiState().apply {
            title = "Live TV"
            showInfoButton = true
        }
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayModals(state, tv = true)
            }
        }
        composeRule.runOnIdle {
            state.showEpgDetail(
                context,
                Event(
                    title = "Tagesschau",
                    serviceName = "Das Erste HD",
                    description = "News",
                    descriptionExtended = buildString {
                        repeat(80) { append("Line $it\n") }
                    },
                    startReadable = "20:00",
                    durationReadable = "15"
                )
            )
        }
        composeRule.waitForIdle()

        // The dialog body is the only vertical scroller on screen.
        val body = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        )
        val bodyNode = composeRule.onNodeWithTag(TV_PLAYER_DETAIL_BODY_TAG)

        bodyNode.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(timeoutMillis = 5_000) { body.scrollOffset() > 0f }
        val oneStep = body.scrollOffset()

        // Held-key repeats arrive as more KeyDowns while the previous step is still landing:
        // each must add a step instead of replacing the one in flight.
        bodyNode.performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { body.scrollOffset() >= oneStep * 2.5f }
    }

    @Test
    fun movieInfoOpensComposeSheet() {
        val state = VideoOverlayUiState().apply {
            title = "Recording"
            showInfoButton = true
        }
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayModals(state, tv = false)
            }
        }
        composeRule.runOnIdle {
            state.showMovieDetail(
                Movie(
                    title = "Tatort",
                    serviceName = "Das Erste HD",
                    description = "Crime",
                    fileName = "tatort.ts"
                )
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tatort").assertIsDisplayed()
        composeRule.onNodeWithText("Crime").assertIsDisplayed()
        composeRule.onNodeWithTag(MOVIE_DETAIL_CAPPED_TAG).assertExists()
    }

    @Test
    fun tvOverlayMovieInfoIsADialog() {
        val state = VideoOverlayUiState().apply {
            title = "Recording"
            showInfoButton = true
        }
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayModals(state, tv = true)
            }
        }
        composeRule.runOnIdle {
            state.showMovieDetail(
                Movie(
                    title = "Tatort",
                    serviceName = "Das Erste HD",
                    description = "Crime",
                    fileName = "tatort.ts"
                )
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tatort").assertIsDisplayed()
        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_DIALOG_TAG).assertExists()
        composeRule.onNodeWithTag(MOVIE_DETAIL_UNCAPPED_TAG).assertExists()
        composeRule.onNodeWithTag(MOVIE_DETAIL_CAPPED_TAG).assertDoesNotExist()
    }

    private fun seekableState(progress: Int) = VideoOverlayUiState().apply {
        title = "Recording"
        showPvrControls = true
        progressMax = 100
        this.progress = progress
        progressEnabled = true
        seekable = true
    }

    private fun setSeekableOverlay(state: VideoOverlayUiState, onSeekChange: (Int) -> Unit) {
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayScreen(
                    state = state,
                    onPlay = {},
                    onRewind = {},
                    onForward = {},
                    onInfo = {},
                    onList = {},
                    onAudio = {},
                    onSubtitle = {},
                    onSleepTimer = {},
                    onSeekChange = onSeekChange
                )
            }
        }
    }

    private fun SemanticsNodeInteraction.progress(): Float =
        fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun SemanticsNodeInteraction.scrollOffset(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun rgbDistance(a: Int, b: Int): Int {
        val ar = (a shr 16) and 0xff
        val ag = (a shr 8) and 0xff
        val ab = a and 0xff
        val br = (b shr 16) and 0xff
        val bg = (b shr 8) and 0xff
        val bb = b and 0xff
        return abs(ar - br) + abs(ag - bg) + abs(ab - bb)
    }
}
