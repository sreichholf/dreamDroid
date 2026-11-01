package net.reichholf.dreamdroid.ui.video

import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Date
import kotlin.Metadata
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.tv.ui.TV_PLAYER_DETAIL_BODY_TAG
import net.reichholf.dreamdroid.tv.ui.TV_PLAYER_DETAIL_DIALOG_TAG
import net.reichholf.dreamdroid.tv.ui.nowEventTimes
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
    fun liveShowsChannelTimesProgrammeClockAndActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = liveState()
        setOverlay(state)

        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText(nowEventTimes(context, NOW_EVENT, NOW_MS)!!)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Wetter vor acht").assertIsDisplayed()
        composeRule.onNodeWithText("21:45  Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithTag(VIDEO_OVERLAY_CLOCK_TAG)
            .assertTextEquals(DateFormat.getTimeFormat(context).format(Date(NOW_MS)))
        composeRule.onNodeWithTag(VIDEO_OVERLAY_PROGRESS_TAG).assertIsDisplayed()
        for (label in listOf("Pause", "Audio Tracks", "Subtitles", "Info", "Services")) {
            composeRule.onNodeWithContentDescription(label)
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
        composeRule.onNodeWithContentDescription("Sleep Timer").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Seek").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Rewind").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Forward").assertDoesNotExist()
    }

    @Test
    fun liveActionsStartWithPlayPause() {
        setOverlay(liveState())
        val play = composeRule.onNodeWithContentDescription("Pause").getUnclippedBoundsInRoot()
        val audio =
            composeRule.onNodeWithContentDescription("Audio Tracks").getUnclippedBoundsInRoot()
        val sleep =
            composeRule.onNodeWithContentDescription("Sleep Timer").getUnclippedBoundsInRoot()
        assertTrue(play.right <= audio.left)
        assertTrue(audio.right <= sleep.left)
    }

    @Test
    fun recordingShowsSubtitleSeekBarTimesAndTransportWithoutProgressEdge() {
        val state = recordingState()
        setOverlay(state)

        composeRule.onNodeWithText("Tatort: Murot").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD · So, 27.10. - 20:15").assertIsDisplayed()
        composeRule.onNodeWithText("34:12").assertIsDisplayed()
        composeRule.onNodeWithText("−54:08").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Seek").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rewind").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Forward").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Audio Tracks").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Info").assertIsDisplayed()
        composeRule.onNodeWithTag(VIDEO_OVERLAY_PROGRESS_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Services").assertDoesNotExist()
    }

    @Test
    fun recordingTransportSitsAboveTheActionsInPortrait() {
        setOverlay(recordingState(), VideoOverlayLayout.Portrait)
        val rewind = composeRule.onNodeWithContentDescription("Rewind").getUnclippedBoundsInRoot()
        val audio =
            composeRule.onNodeWithContentDescription("Audio Tracks").getUnclippedBoundsInRoot()
        assertTrue(rewind.bottom <= audio.top)
    }

    @Test
    fun recordingTransportAndActionsShareARowInLandscape() {
        setOverlay(recordingState(), VideoOverlayLayout.Landscape)
        val rewind = composeRule.onNodeWithContentDescription("Rewind").getUnclippedBoundsInRoot()
        val audio =
            composeRule.onNodeWithContentDescription("Audio Tracks").getUnclippedBoundsInRoot()
        val rewindCenter = (rewind.top + rewind.bottom) / 2
        val audioCenter = (audio.top + audio.bottom) / 2
        assertTrue(
            "rewind $rewind and audio $audio",
            abs(rewindCenter.value - audioCenter.value) < 2f
        )
        assertTrue(rewind.right <= audio.left)
    }

    @Test
    fun liveActionsSitBelowTheProgrammeInPortrait() {
        setOverlay(liveState(), VideoOverlayLayout.Portrait)
        val next = composeRule.onNodeWithText("21:45  Tagesschau").getUnclippedBoundsInRoot()
        val play = composeRule.onNodeWithContentDescription("Pause").getUnclippedBoundsInRoot()
        assertTrue(next.bottom <= play.top)
    }

    @Test
    fun liveActionsSitBesideTheProgrammeInLandscape() {
        setOverlay(liveState(), VideoOverlayLayout.Landscape)
        val title = composeRule.onNodeWithText("Wetter vor acht").getUnclippedBoundsInRoot()
        val next = composeRule.onNodeWithText("21:45  Tagesschau").getUnclippedBoundsInRoot()
        val play = composeRule.onNodeWithContentDescription("Pause").getUnclippedBoundsInRoot()
        assertTrue("play $play next $next", play.top < next.bottom)
        assertTrue("play $play title $title", play.left >= title.right)
    }

    @Test
    fun narrowLandscapeKeepsTheChannelAndMovesActionsBelowTheProgramme() {
        setOverlay(liveState(), VideoOverlayLayout.Landscape, landscapeWidth = 640.dp)
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        val title = composeRule.onNodeWithText("Das Erste HD").getUnclippedBoundsInRoot()
        assertTrue("title $title", title.right - title.left > 48.dp)
        val next = composeRule.onNodeWithText("21:45  Tagesschau").getUnclippedBoundsInRoot()
        val play = composeRule.onNodeWithContentDescription("Pause").getUnclippedBoundsInRoot()
        assertTrue("play $play next $next", next.bottom <= play.top)
        for (label in listOf("Audio Tracks", "Subtitles", "Info", "Services", "Sleep Timer")) {
            composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        }
    }

    @Test
    fun recordingShowsItsTimesBeforeTheEngineReportsALength() {
        val state = recordingState().apply {
            progressMax = 0
            progress = 0
        }
        setOverlay(state)
        composeRule.onNodeWithContentDescription("Seek").assertDoesNotExist()
        composeRule.onNodeWithText("34:12").assertIsDisplayed()
        composeRule.onNodeWithText("\u221254:08").assertIsDisplayed()
    }

    @Test
    fun tabletShowsTheProgrammeDescription() {
        setOverlay(liveState(), VideoOverlayLayout.Tablet)
        composeRule.onNodeWithText(NOW_EVENT.description).assertIsDisplayed()
    }

    @Test
    fun phoneLeavesTheProgrammeDescriptionOut() {
        setOverlay(liveState(), VideoOverlayLayout.Portrait)
        composeRule.onNodeWithText(NOW_EVENT.description).assertDoesNotExist()
    }

    @Test
    fun hiddenActionsStayHidden() {
        val state = liveState().apply {
            showAudioButton = false
            showSubtitleButton = false
            showInfoButton = false
            showListButton = false
        }
        setOverlay(state)
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sleep Timer").assertIsDisplayed()
        for (label in listOf("Audio Tracks", "Subtitles", "Info", "Services")) {
            composeRule.onNodeWithContentDescription(label).assertDoesNotExist()
        }

        composeRule.runOnIdle {
            state.recording = true
            state.showPvrControls = false
        }
        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sleep Timer").assertIsDisplayed()
        for (label in listOf("Rewind", "Forward", "Audio Tracks", "Subtitles", "Info")) {
            composeRule.onNodeWithContentDescription(label).assertDoesNotExist()
        }
    }

    @Test
    fun actionsReportClicks() {
        val clicks = mutableListOf<String>()
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayScreen(
                    state = liveState(),
                    onPlay = { clicks += "play" },
                    onRewind = {},
                    onForward = {},
                    onInfo = { clicks += "info" },
                    onList = { clicks += "list" },
                    onAudio = { clicks += "audio" },
                    onSubtitle = { clicks += "subtitle" },
                    onSleepTimer = { clicks += "sleep" },
                    onSeekChange = {},
                    layout = VideoOverlayLayout.Portrait,
                    nowMs = NOW_MS
                )
            }
        }
        for (label in listOf(
            "Pause",
            "Audio Tracks",
            "Subtitles",
            "Info",
            "Services",
            "Sleep Timer"
        )) {
            composeRule.onNodeWithContentDescription(label).performClick()
        }
        composeRule.runOnIdle {
            assertEquals(listOf("play", "audio", "subtitle", "info", "list", "sleep"), clicks)
        }
    }

    @Test
    fun playButtonShowsPauseOnlyWhilePlayingForward() {
        val state = VideoOverlayUiState().apply {
            title = "Tatort: Recording"
            recording = true
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

    private fun setOverlay(
        state: VideoOverlayUiState,
        layout: VideoOverlayLayout = VideoOverlayLayout.Portrait,
        landscapeWidth: Dp = 844.dp
    ) {
        // A landscape phone's width, whatever the test device's orientation.
        val width = if (layout == VideoOverlayLayout.Landscape) {
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .requiredWidth(landscapeWidth)
        } else {
            Modifier
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
                    onSeekChange = {},
                    modifier = width,
                    layout = layout,
                    nowMs = NOW_MS
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
                    onSeekChange = {},
                    layout = VideoOverlayLayout.Portrait
                )
            }
        }

        composeRule.onNodeWithContentDescription("Sleep Timer").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
        composeRule.onNodeWithTag(VIDEO_OVERLAY_SLEEP_BADGE_TAG).assertDoesNotExist()

        state.sleepTimer = SleepTimer.Running(29)
        composeRule.onNodeWithContentDescription("Sleep Timer: 29 min. left")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(VIDEO_OVERLAY_SLEEP_BADGE_TAG).assertIsDisplayed()
        // The label says it already; accessibility (the merged tree) does not read the badge.
        composeRule.onNodeWithText("29").assertDoesNotExist()
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
    fun livePanelIsTranslucentSurfaceContainerLowWithAPrimaryProgressEdge() {
        var panel = Color.Unspecified
        var primary = Color.Unspecified
        val state = liveState().apply { progress = 1200 }
        composeRule.setContent {
            DreamDroidTheme {
                panel = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f)
                    .compositeOver(Color.Black)
                primary = MaterialTheme.colorScheme.primary
                Box(Modifier.background(Color.Black)) {
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
                        onSeekChange = {},
                        layout = VideoOverlayLayout.Portrait,
                        nowMs = NOW_MS
                    )
                }
            }
        }
        val bitmap = composeRule.onNodeWithTag(VIDEO_OVERLAY_PANEL_TAG)
            .captureToImage()
            .asAndroidBitmap()
        // The bottom padding strip, below the actions and clear of the rounded corners.
        val y = bitmap.height - 3
        var x = bitmap.width / 5
        while (x < bitmap.width * 4 / 5) {
            val px = bitmap.getPixel(x, y)
            assertTrue(
                "panel pixel #${Integer.toHexString(px)} at $x should be " +
                    "#${Integer.toHexString(panel.toArgb())}",
                rgbDistance(px, panel.toArgb()) < 12
            )
            x += 7
        }
        val edge = bitmap.getPixel(bitmap.width / 4, 1)
        assertTrue(
            "progress edge #${Integer.toHexString(edge)} should be primary " +
                "#${Integer.toHexString(primary.toArgb())}",
            rgbDistance(edge, primary.toArgb()) < 40
        )
    }

    @Test
    fun topBarBackAndPictureInPictureAreButtons() {
        var backs = 0
        var pips = 0
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayTopBar(
                    onBack = { backs++ },
                    onPictureInPicture = { pips++ },
                    layout = VideoOverlayLayout.Portrait
                )
            }
        }
        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        composeRule.onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .assert(isButton)
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithContentDescription("Picture in picture")
            .assertIsDisplayed()
            .assert(isButton)
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, backs)
            assertEquals(1, pips)
        }
        val back = composeRule.onNodeWithContentDescription("Back").getUnclippedBoundsInRoot()
        val pip = composeRule.onNodeWithContentDescription("Picture in picture")
            .getUnclippedBoundsInRoot()
        assertTrue(back.right < pip.left)
        assertTrue(back.right - back.left >= 48.dp && pip.right - pip.left >= 48.dp)
    }

    @Test
    fun openChannelListKeepsOnlyTheTabletPictureInPicture() {
        var layout by mutableStateOf(VideoOverlayLayout.Tablet)
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayTopBar(
                    onBack = {},
                    onPictureInPicture = {},
                    channelListOpen = true,
                    layout = layout
                )
            }
        }
        composeRule.onNodeWithContentDescription("Picture in picture").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()

        composeRule.runOnIdle { layout = VideoOverlayLayout.Portrait }
        composeRule.onNodeWithContentDescription("Picture in picture").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun topBarHidesPictureInPictureWhereUnsupported() {
        composeRule.setContent {
            DreamDroidTheme {
                VideoOverlayTopBar(
                    onBack = {},
                    onPictureInPicture = null,
                    layout = VideoOverlayLayout.Landscape
                )
            }
        }
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Picture in picture").assertDoesNotExist()
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

    private fun liveState() = VideoOverlayUiState().apply {
        title = "Das Erste HD"
        nowTitle = "Wetter vor acht"
        nowStart = "20:15"
        showNow = true
        nowEvent = NOW_EVENT
        nextStart = "21:45"
        nextTitle = "Tagesschau"
        hasNext = true
        progressMax = 1800
        progress = 600
        progressEnabled = true
        isPlaying = true
        showAudioButton = true
        showSubtitleButton = true
        showInfoButton = true
        showListButton = true
    }

    private fun recordingState() = VideoOverlayUiState().apply {
        title = "Tatort: Murot"
        recording = true
        subtitle = "Das Erste HD · So, 27.10. - 20:15"
        nowStart = "34:12"
        remaining = "−54:08"
        showPvrControls = true
        progressMax = 5280
        progress = 2052
        progressEnabled = true
        seekable = true
        showAudioButton = true
        showInfoButton = true
    }

    private fun seekableState(progress: Int) = VideoOverlayUiState().apply {
        title = "Recording"
        recording = true
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
                    onSeekChange = onSeekChange,
                    layout = VideoOverlayLayout.Portrait
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

    private companion object {
        const val START_SEC = 1_700_000_000L
        const val NOW_MS = (START_SEC + 600) * 1000

        val NOW_EVENT = Event(
            eventId = "1",
            title = "Wetter vor acht",
            description = "Aussichten fuer das Wochenende",
            start = START_SEC.toString(),
            duration = "1800"
        )
    }
}
