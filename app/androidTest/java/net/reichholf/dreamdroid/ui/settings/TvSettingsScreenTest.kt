package net.reichholf.dreamdroid.ui.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
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
        composeRule.setContent {
            DreamDroidTvTheme {
                TvSettingsScreen(settings = settings, onChange = { settings = it(settings) })
            }
        }

        val setting = composeRule.onNodeWithTag("tv_setting_integrated_video_player")
        setting.assertIsFocused()
        setting.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_setting_video_hardware_acceleration").assertIsFocused()
        assertTrue(settings.integratedVideoPlayer)

        setting.requestFocus()
        setting.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertFalse(settings.integratedVideoPlayer)
    }

    @Test
    fun autoStartLastStreamChangesOnlyWhenActivatedWithCenter() {
        var settings by mutableStateOf(AppSettings())
        composeRule.setContent {
            DreamDroidTvTheme {
                TvSettingsScreen(settings = settings, onChange = { settings = it(settings) })
            }
        }

        val setting = composeRule.onNodeWithTag("tv_setting_auto_start_last_stream")
        setting.performScrollTo()
        setting.requestFocus()
        setting.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertTrue(settings.autoStartLastStream)
    }

    @Test
    fun listSettingOpensOnlyWhenActivatedWithCenter() {
        composeRule.setContent {
            DreamDroidTvTheme {
                TvSettingsScreen(settings = AppSettings(), onChange = {})
            }
        }

        val setting = composeRule.onNodeWithTag("tv_setting_video_hardware_acceleration")
        setting.requestFocus()
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
        composeRule.setContent {
            DreamDroidTvTheme {
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
        }

        composeRule.onNodeWithTag("tv_setting_preferred_audio_language")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Preferred audio language").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("eng")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        assertEquals("eng", settings.preferredAudioLanguage)
    }

    @Test
    fun hwAccelDisabledWhenIntegratedPlayerOff() {
        composeRule.setContent {
            DreamDroidTvTheme {
                TvSettingsScreen(
                    settings = AppSettings(integratedVideoPlayer = false),
                    onChange = {}
                )
            }
        }

        composeRule.onNodeWithText("Accelerated decoding").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Hardware Acceleration").assertCountEquals(0)
    }

    @Test
    fun syncPathDialogEditsTheDraft() {
        val draft = TextFieldState("/usr/share/enigma2/picon")
        var editing by mutableStateOf(false)
        var confirmed = false
        composeRule.setContent {
            DreamDroidTvTheme {
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
        }

        composeRule.onNodeWithText("/usr/share/enigma2/picon").performScrollTo().performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("/media/hdd/picon")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        assertTrue(confirmed)
        assertEquals("/media/hdd/picon", draft.text.toString())
    }
}
