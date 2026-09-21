package net.reichholf.dreamdroid.ui.video

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.Metadata
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.ui.epg.EPG_DETAIL_CAPPED_TAG
import net.reichholf.dreamdroid.ui.epg.EPG_DETAIL_UNCAPPED_TAG
import net.reichholf.dreamdroid.ui.movies.MOVIE_DETAIL_CAPPED_TAG
import net.reichholf.dreamdroid.ui.movies.MOVIE_DETAIL_UNCAPPED_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import net.reichholf.dreamdroid.ui.video.VideoOverlayController
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
            // Live-style chrome: progress only (no PVR buttons) — the blue nest was most obvious.
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
                VideoOverlayScreen(
                    state = state,
                    onPlay = {},
                    onRewind = {},
                    onForward = {},
                    onInfo = {},
                    onList = {},
                    onAudio = {},
                    onSubtitle = {},
                    onSeekChange = {}
                )
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
    fun tvOverlayEpgSheetHasNoHeightCap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = VideoOverlayUiState().apply {
            title = "Live TV"
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
                    onSeekChange = {},
                    uncappedDetailSheets = true
                )
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
        composeRule.onNodeWithTag(EPG_DETAIL_UNCAPPED_TAG).assertExists()
        composeRule.onNodeWithTag(EPG_DETAIL_CAPPED_TAG).assertDoesNotExist()
    }

    @Test
    fun movieInfoOpensComposeSheet() {
        val state = VideoOverlayUiState().apply {
            title = "Recording"
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
                    onSeekChange = {}
                )
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
    fun tvOverlayMovieSheetHasNoHeightCap() {
        val state = VideoOverlayUiState().apply {
            title = "Recording"
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
                    onSeekChange = {},
                    uncappedDetailSheets = true
                )
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
        composeRule.onNodeWithTag(MOVIE_DETAIL_UNCAPPED_TAG).assertExists()
        composeRule.onNodeWithTag(MOVIE_DETAIL_CAPPED_TAG).assertDoesNotExist()
    }

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
