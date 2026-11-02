package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.epg.EPG_DETAIL_CAPPED_TAG
import net.reichholf.dreamdroid.ui.epg.EpgDetailContent
import net.reichholf.dreamdroid.ui.movies.MOVIE_DETAIL_CAPPED_TAG
import net.reichholf.dreamdroid.ui.movies.MOVIE_DETAIL_UNCAPPED_TAG
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The player's EPG and recording dialogs on TV: their content, Close, and the D-pad scrolling
 * of the body.
 */
class TvPlayerDetailDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var inputModeManager: InputModeManager

    @Test
    fun epgDialogShowsTheProgrammeAndItsCloseButton() {
        composeRule.setContent {
            DreamDroidTvTheme {
                TvPlayerEpgDetailDialog(content = epgContent(), onDismiss = {})
            }
        }

        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_BODY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertIsDisplayed()
        composeRule.onNodeWithText("20:00 (15 min)").assertIsDisplayed()
        composeRule.onNodeWithText(closeLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(EPG_DETAIL_CAPPED_TAG).assertDoesNotExist()
    }

    @Test
    fun epgDialogCloseDismisses() {
        var dismissals = 0
        composeRule.setContent {
            DreamDroidTvTheme {
                TvPlayerEpgDetailDialog(content = epgContent(), onDismiss = { dismissals++ })
            }
        }

        composeRule.onNodeWithText(closeLabel()).performClick()

        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun movieDialogShowsTheRecordingAndItsCloseButton() {
        composeRule.setContent {
            DreamDroidTvTheme {
                TvPlayerMovieDetailDialog(content = movieContent(), onDismiss = {})
            }
        }

        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_BODY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MOVIE_DETAIL_UNCAPPED_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Tatort").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Crime").assertIsDisplayed()
        composeRule.onNodeWithText("90 min").assertIsDisplayed()
        composeRule.onNodeWithText(closeLabel()).assertIsDisplayed()
        composeRule.onNodeWithTag(MOVIE_DETAIL_CAPPED_TAG).assertDoesNotExist()
    }

    @Test
    fun movieDialogCloseDismisses() {
        var dismissals = 0
        composeRule.setContent {
            DreamDroidTvTheme {
                TvPlayerMovieDetailDialog(content = movieContent(), onDismiss = { dismissals++ })
            }
        }

        composeRule.onNodeWithText(closeLabel()).performClick()

        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun bodyTakesFocusWhenTheDialogOpens() {
        setKeyboardContent {
            TvPlayerEpgDetailDialog(content = epgContent(), onDismiss = {})
        }

        composeRule.onNodeWithTag(TV_PLAYER_DETAIL_BODY_TAG).assertIsFocused()
        composeRule.onNodeWithText(closeLabel()).assertIsNotFocused()
    }

    @Test
    fun epgBodyScrollsDownAndBackUpWithTheDpad() {
        setKeyboardContent {
            TvPlayerEpgDetailDialog(content = epgContent(lines = 80), onDismiss = {})
        }

        val body = composeRule.onNodeWithTag(TV_PLAYER_DETAIL_BODY_TAG)
        // The body's scrolling Column is the only vertical scroller in the dialog.
        val scroller = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        )
        assertTrue("the body starts unscrolled", scroller.scrollOffset() == 0f)

        body.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(timeoutMillis = 5_000) { scroller.scrollOffset() > 0f }
        body.assertIsFocused()

        body.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitUntil(timeoutMillis = 5_000) { scroller.scrollOffset() == 0f }
        body.assertIsFocused()
    }

    @Test
    fun movieBodyScrollsDownAndBackUpWithTheDpad() {
        setKeyboardContent {
            TvPlayerMovieDetailDialog(content = movieContent(lines = 80), onDismiss = {})
        }

        val body = composeRule.onNodeWithTag(TV_PLAYER_DETAIL_BODY_TAG)
        // The recording's Column scrolls with the state the dialog owns.
        val scroller = composeRule.onNodeWithTag(MOVIE_DETAIL_UNCAPPED_TAG)
        assertTrue("the body starts unscrolled", scroller.scrollOffset() == 0f)

        body.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitUntil(timeoutMillis = 5_000) { scroller.scrollOffset() > 0f }
        body.assertIsFocused()

        body.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitUntil(timeoutMillis = 5_000) { scroller.scrollOffset() == 0f }
        body.assertIsFocused()
    }

    /**
     * Composes [content] once the window is in keyboard input mode, as a remote drives the
     * player. In touch mode a clickable confirm button cannot take focus, so the focus
     * assertions would prove nothing.
     */
    private fun setKeyboardContent(content: @Composable () -> Unit) {
        var show by mutableStateOf(false)
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTvTheme {
                if (show) {
                    content()
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle {
            assertEquals(InputMode.Keyboard, inputModeManager.inputMode)
            show = true
        }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.scrollOffset(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun closeLabel(): String = string(R.string.close)

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun epgContent(lines: Int = 0) = EpgDetailContent(
        title = "Tagesschau",
        serviceName = "Das Erste HD",
        description = "News",
        descriptionExtended = longText(lines),
        dateLine = "20:00 (15 min)",
        isNext = false
    )

    private fun movieContent(lines: Int = 0) = MovieDetailContent(
        title = "Tatort",
        serviceName = "Das Erste HD",
        description = "Crime",
        descriptionExtended = longText(lines),
        tags = emptyList(),
        length = "90 min",
        date = "So, 27.10. 20:15",
        fileSize = "3.4 GB"
    )

    private fun longText(lines: Int): String = buildString {
        repeat(lines) { append("Line $it\n") }
    }
}
