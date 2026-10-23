package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.video.SleepTimer
import net.reichholf.dreamdroid.ui.video.VideoOverlayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvPlayerInfobarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context get() = composeRule.activity

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_DYNAMIC_THEME_COLORS, false)
            .commit()
    }

    @Test
    fun liveStateShowsChannelEventsAndMeta() {
        val event = runningEvent()
        val state = liveState().apply { nowEvent = event }
        setInfobar(state)

        val meta = nowEventTimes(context, event)
        assertNotNull(meta)
        composeRule.onNodeWithTag(TV_PLAYER_INFOBAR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText(meta!!).assertIsDisplayed()
        composeRule.onNodeWithText("20:15").assertIsDisplayed()
        composeRule.onNodeWithText("Tatort").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_player_infobar_progress", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("tv_player_infobar_clock", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun liveMetaFallsBackToNowStartWithoutEvent() {
        val state = liveState().apply { nowStart = "20:00" }
        setInfobar(state)
        composeRule.onNodeWithText("20:00").assertIsDisplayed()
    }

    @Test
    fun actionButtonsHaveLabelsAndFireCallbacks() {
        val clicks = mutableListOf<String>()
        val state = liveState()
        setInfobar(
            state,
            onAudio = { clicks += "audio" },
            onSubtitle = { clicks += "subtitle" },
            onInfo = { clicks += "info" },
            onList = { clicks += "list" },
            onSleepTimer = { clicks += "sleep" }
        )
        listOf(
            R.string.audio_tracks,
            R.string.subtitles,
            R.string.info,
            R.string.services,
            R.string.sleeptimer
        ).forEach { res ->
            composeRule.onNodeWithContentDescription(context.getString(res))
                .assertIsDisplayed()
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                // tv Surfaces take OK, not touch; the semantics action is OK's path.
                .performSemanticsAction(SemanticsActions.OnClick)
        }
        composeRule.runOnIdle {
            assertEquals(listOf("audio", "subtitle", "info", "list", "sleep"), clicks)
        }
    }

    @Test
    fun hiddenActionsAreAbsent() {
        val state = liveState().apply {
            showAudioButton = false
            showSubtitleButton = false
            showInfoButton = false
            showListButton = false
        }
        setInfobar(state)
        listOf(R.string.audio_tracks, R.string.subtitles, R.string.info, R.string.services)
            .forEach { res ->
                composeRule.onNodeWithContentDescription(context.getString(res))
                    .assertDoesNotExist()
            }
        composeRule.onNodeWithContentDescription(context.getString(R.string.sleeptimer))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.play))
            .assertDoesNotExist()
    }

    @Test
    fun recordingShowsTransportAndFocusesPlay() {
        val requester = FocusRequester()
        val clicks = mutableListOf<String>()
        val state = VideoOverlayUiState().apply {
            title = "Tatort: Recording"
            nowTitle = "Das Erste HD"
            nowStart = "0:12:03"
            nowDuration = "1:28:00"
            showPvrControls = true
            progressMax = 100
            progress = 13
            showAudioButton = true
        }
        val inputModeManager = setInfobar(
            state,
            requester = requester,
            onPlay = { clicks += "play" },
            onRewind = { clicks += "rewind" },
            onForward = { clicks += "forward" }
        )
        enterKeyboardMode(inputModeManager)

        composeRule.onNodeWithText("Tatort: Recording").assertIsDisplayed()
        composeRule.onNodeWithText("0:12:03").assertIsDisplayed()
        composeRule.onNodeWithText("1:28:00").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_player_infobar_progress", useUnmergedTree = true)
            .assertIsDisplayed()
        listOf(R.string.rewind, R.string.play, R.string.forward).forEach { res ->
            composeRule.onNodeWithContentDescription(context.getString(res))
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                // tv Surfaces take OK, not touch; the semantics action is OK's path.
                .performSemanticsAction(SemanticsActions.OnClick)
        }
        composeRule.runOnIdle {
            assertEquals(listOf("rewind", "play", "forward"), clicks)
            assertTrue(requester.requestFocus())
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.play))
            .assertIsFocused()
    }

    @Test
    fun focusedActionShowsItsLabel() {
        val requester = FocusRequester()
        val state = liveState()
        val inputModeManager = setInfobar(state, requester = requester)
        val audio = context.getString(R.string.audio_tracks)
        composeRule.onNodeWithText(audio, useUnmergedTree = true).assertDoesNotExist()

        enterKeyboardMode(inputModeManager)
        composeRule.runOnIdle { assertTrue(requester.requestFocus()) }

        composeRule.onNodeWithContentDescription(audio).assertIsFocused()
        composeRule.onNodeWithText(audio, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.subtitles), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun runningSleepTimerShowsMinutesBadge() {
        val state = liveState().apply { sleepTimer = SleepTimer.Running(27) }
        setInfobar(state)
        composeRule.onNodeWithContentDescription(context.getString(R.string.sleep_timer_left, 27))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("tv_player_infobar_sleep_badge", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("27", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun bindTvPlayerInfobarBridgesViewFocusIntoFirstControl() {
        val state = liveState().apply { showAudioButton = false }
        var composeView: ComposeView? = null
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            AndroidView(
                factory = { ctx ->
                    ComposeView(ctx).also { view ->
                        composeView = view
                        view.bindTvPlayerInfobar(
                            state = state,
                            onPlay = {},
                            onRewind = {},
                            onForward = {},
                            onInfo = {},
                            onList = {},
                            onAudio = {},
                            onSubtitle = {},
                            onSleepTimer = {}
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        enterKeyboardMode(inputModeManager)
        val view = composeView!!
        assertTrue(view.isFocusable)
        assertTrue(view.isFocusableInTouchMode)
        composeRule.runOnIdle { view.requestFocus() }
        val subtitles = context.getString(R.string.subtitles)
        composeRule.waitUntil {
            composeRule.onAllNodesWithText(subtitles, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(subtitles).assertIsFocused()
    }

    @Test
    fun movingBetweenActionsResetsAutoHide() {
        val state = liveState().apply { showAudioButton = false }
        var composeView: ComposeView? = null
        var interactions = 0
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            AndroidView(
                factory = { ctx ->
                    ComposeView(ctx).also { view ->
                        composeView = view
                        view.bindTvPlayerInfobar(
                            state = state,
                            onPlay = {},
                            onRewind = {},
                            onForward = {},
                            onInfo = {},
                            onList = {},
                            onAudio = {},
                            onSubtitle = {},
                            onSleepTimer = {},
                            onUserInteraction = { interactions++ }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        enterKeyboardMode(inputModeManager)
        composeRule.runOnIdle { composeView!!.requestFocus() }
        val subtitles = composeRule.onNodeWithContentDescription(
            context.getString(R.string.subtitles)
        )
        composeRule.waitUntil { runCatching { subtitles.assertIsFocused() }.isSuccess }
        val before = composeRule.runOnIdle { interactions }
        subtitles.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithContentDescription(context.getString(R.string.info))
            .assertIsFocused()
        composeRule.runOnIdle { assertTrue(interactions > before) }
    }

    private fun setInfobar(
        state: VideoOverlayUiState,
        requester: FocusRequester? = null,
        onPlay: () -> Unit = {},
        onRewind: () -> Unit = {},
        onForward: () -> Unit = {},
        onInfo: () -> Unit = {},
        onList: () -> Unit = {},
        onAudio: () -> Unit = {},
        onSubtitle: () -> Unit = {},
        onSleepTimer: () -> Unit = {}
    ): InputModeManager {
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            // A TV window: the phone emulator squeezes the text column next to the actions.
            DeviceConfigurationOverride(DeviceConfigurationOverride.WindowSize(TvWindow)) {
                DreamDroidTvTheme(fillBackground = false, forceDark = true) {
                    TvPlayerInfobar(
                        state = state,
                        onPlay = onPlay,
                        onRewind = onRewind,
                        onForward = onForward,
                        onInfo = onInfo,
                        onList = onList,
                        onAudio = onAudio,
                        onSubtitle = onSubtitle,
                        onSleepTimer = onSleepTimer,
                        firstControlFocusRequester = requester
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return inputModeManager
    }

    // D-pad focus is asserted in keyboard input mode, see docs/tv-focus-testing.md.
    private fun enterKeyboardMode(inputModeManager: InputModeManager) {
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
    }

    private fun liveState() = VideoOverlayUiState().apply {
        title = "Das Erste HD"
        nowTitle = "Tagesschau"
        showNow = true
        hasNext = true
        nextStart = "20:15"
        nextTitle = "Tatort"
        zapCurrentRef = "1:0:19:283D:3FB:1:C00000:0:0:0:"
        progressMax = 100
        progress = 40
        showAudioButton = true
        showSubtitleButton = true
        showInfoButton = true
        showListButton = true
    }

    private fun runningEvent(): Event {
        val start = 1_760_000_000L
        return Event(
            title = "Tagesschau",
            start = start.toString(),
            duration = "3600",
            currentTime = (start + 600).toString()
        )
    }
}

private val TvWindow = DpSize(960.dp, 540.dp)
