package net.reichholf.dreamdroid.ui.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AppSettings
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TvSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun tvPreferenceTitlesVisible() {
        composeRule.setContent {
            DreamDroidTvTheme {
                TvSettingsScreen(settings = AppSettings(), onChange = {})
            }
        }

        composeRule.onNodeWithText("Video Player").assertIsDisplayed()
        composeRule.onNodeWithText("Integrated video player", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Accelerated decoding").assertIsDisplayed()
        composeRule.onNodeWithText("Picons").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Picons by service name").performScrollTo().assertIsDisplayed()
        // Phone hub chrome; not part of the television prefs subset.
        composeRule.onAllNodesWithText("Now-playing strip").assertCountEquals(0)
    }

    @Test
    fun integratedPlayerOnlyChangesWhenActivatedWithCenter() {
        var settings by mutableStateOf(AppSettings())
        assertTrue(settings.integratedVideoPlayer)
        setDpadContent {
            TvSettingsScreen(settings = settings, onChange = { settings = it(settings) })
        }

        val setting = composeRule.onNodeWithTag("tv_setting_integrated_video_player")
        setting.assertIsFocused()
        setting.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        setting.assertIsNotFocused()
        assertTrue(settings.integratedVideoPlayer)

        setting.requestFocus()
        setting.assertIsFocused()
        setting.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertFalse(settings.integratedVideoPlayer)
    }

    @Test
    fun autoStartLastStreamChangesOnlyWhenActivatedWithCenter() {
        var settings by mutableStateOf(AppSettings())
        setDpadContent {
            TvSettingsScreen(settings = settings, onChange = { settings = it(settings) })
        }

        val setting = composeRule.onNodeWithTag("tv_setting_auto_start_last_stream")
        setting.performScrollTo()
        setting.requestFocus()
        setting.assertIsFocused()
        setting.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        assertFalse(settings.autoStartLastStream)

        setting.requestFocus()
        setting.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertTrue(settings.autoStartLastStream)
    }

    @Test
    fun streamBufferBelongsToTheVideoPlayer() {
        setDpadContent {
            TvSettingsScreen(settings = AppSettings(), onChange = {})
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun top(title: Int) = composeRule.onNodeWithText(context.getString(title))
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(top(R.string.use_hw_accel) < top(R.string.stream_buffer))
        assertTrue(top(R.string.stream_buffer) < top(R.string.usability))
    }

    @Test
    fun listSettingOpensOnlyWhenActivatedWithCenter() {
        setDpadContent {
            TvSettingsScreen(settings = AppSettings(), onChange = {})
        }

        val setting = composeRule.onNodeWithTag("tv_setting_video_hardware_acceleration")
        setting.requestFocus()
        setting.assertIsFocused()
        setting.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Hardware Acceleration").assertCountEquals(0)

        setting.requestFocus()
        setting.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hardware Acceleration").assertIsDisplayed()
    }

    @Test
    fun preferredAudioLanguageCanBeEnteredAndSaved() {
        var settings by mutableStateOf(AppSettings())
        var editing by mutableStateOf(false)
        val draft = TextFieldState()
        setDpadContent {
            TvSettingsScreen(
                settings = settings,
                onChange = { settings = it(settings) },
                preferredAudioLanguageDraft = draft.takeIf { editing },
                onEditPreferredAudioLanguage = { editing = true },
                onConfirmPreferredAudioLanguage = {
                    settings = settings.copy(preferredAudioLanguage = draft.text.toString())
                    editing = false
                },
                onDismissPreferredAudioLanguage = { editing = false }
            )
        }

        activate("tv_setting_preferred_audio_language")
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("eng")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        assertEquals("eng", settings.preferredAudioLanguage)
    }

    @Test
    fun hwAccelDisabledWhenIntegratedPlayerOff() {
        setDpadContent {
            TvSettingsScreen(
                settings = AppSettings(integratedVideoPlayer = false),
                onChange = {}
            )
        }

        activate("tv_setting_video_hardware_acceleration")
        composeRule.onAllNodesWithText("Hardware Acceleration").assertCountEquals(0)
    }

    @Test
    fun syncPathDialogEditsTheDraft() {
        val draft = TextFieldState("/usr/share/enigma2/picon")
        var editing by mutableStateOf(false)
        var confirmed = false
        setDpadContent {
            TvSettingsScreen(
                settings = AppSettings(),
                onChange = {},
                syncPiconsPathDraft = draft.takeIf { editing },
                onEditSyncPiconsPath = { editing = true },
                onConfirmSyncPiconsPath = {
                    confirmed = true
                    editing = false
                }
            )
        }

        activate("tv_setting_sync_picons_path")
        composeRule.onNode(hasSetTextAction()).performTextReplacement("/media/hdd/picon")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        assertTrue(confirmed)
        assertEquals("/media/hdd/picon", draft.text.toString())
    }

    /**
     * Focuses the row [tag] and presses OK, as the remote does. TV surfaces take no touch
     * input (tv-material handles only key events), so a touch click would not open anything.
     */
    private fun activate(tag: String) {
        val row = composeRule.onNodeWithTag(tag)
        row.performScrollTo()
        row.requestFocus()
        row.assertIsFocused()
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
    }

    /**
     * Shows [content] in keyboard input mode, as on a TV remote. In touch mode clickable rows
     * cannot take focus, so D-pad moves would skip them and prove nothing. The screen comes
     * after the switch, so its initial focus request runs in keyboard mode.
     */
    private fun setDpadContent(content: @Composable () -> Unit) {
        var showContent by mutableStateOf(false)
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTvTheme {
                if (showContent) {
                    content()
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle {
            assertEquals(InputMode.Keyboard, inputModeManager.inputMode)
            showContent = true
        }
        composeRule.waitForIdle()
    }
}
