package net.reichholf.dreamdroid.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun preferenceTitlesVisible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = SettingsState.create(context)
        composeRule.setContent {
            DreamDroidTheme {
                SettingsScreen(
                    state = state,
                    onThemeChanged = {},
                    onDynamicColorsChanged = {},
                    onSyncPicons = {},
                )
            }
        }

        composeRule.onNodeWithText("Video Player").assertIsDisplayed()
        composeRule.onNodeWithText("Integrated video player").assertIsDisplayed()
        composeRule.onNodeWithText("Useability").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Day/Night Theme choices").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Picons").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Use Picons").performScrollTo().assertIsDisplayed()
    }
}
