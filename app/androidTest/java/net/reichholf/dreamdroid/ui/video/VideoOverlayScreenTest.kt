package net.reichholf.dreamdroid.ui.video

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VideoOverlayScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
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
                    onSeekChange = {},
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
}
