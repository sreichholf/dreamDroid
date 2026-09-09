package net.reichholf.dreamdroid.ui.screenshot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ScreenshotScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun actionsEnabledShowsReloadShareSave() {
        val state = ScreenshotUiState().apply { actionsEnabled = true }
        composeRule.setContent {
            DreamDroidTheme {
                ScreenshotScreen(
                    state = state,
                    onReload = {},
                    onShare = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Screenshot").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reload").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save").assertIsDisplayed()
    }

    @Test
    fun actionsDisabledHidesToolbarControls() {
        val state = ScreenshotUiState().apply { actionsEnabled = false }
        composeRule.setContent {
            DreamDroidTheme {
                ScreenshotScreen(
                    state = state,
                    onReload = {},
                    onShare = {},
                    onSave = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Screenshot").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reload").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Share").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Save").assertDoesNotExist()
    }
}
