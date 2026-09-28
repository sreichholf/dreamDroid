package net.reichholf.dreamdroid.ui.settings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
    fun integratedPlayerDefaultsOnAndTurnsOff() {
        var settings by mutableStateOf(AppSettings())
        assertTrue(settings.integratedVideoPlayer)
        composeRule.setContent {
            DreamDroidTvTheme {
                TvSettingsScreen(settings = settings, onChange = { settings = it(settings) })
            }
        }

        composeRule.onNode(hasText("Integrated video player") and isToggleable()).performClick()
        composeRule.waitForIdle()
        assertFalse(settings.integratedVideoPlayer)
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
