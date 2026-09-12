package net.reichholf.dreamdroid.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithText("Start screen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Day/Night Theme choices").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Picons").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Use Picons").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("About").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Changelog").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backup").performScrollTo().assertIsDisplayed()
        // Reload FAB / preference removed; list screens use pull-to-refresh only.
        composeRule.onAllNodesWithText("Disable floating reload button").assertCountEquals(0)
    }

    @Test
    fun footerRowsInvokeCallbacks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = SettingsState.create(context)
        var about = false
        var changelog = false
        var backup = false
        composeRule.setContent {
            DreamDroidTheme {
                SettingsScreen(
                    state = state,
                    onThemeChanged = {},
                    onDynamicColorsChanged = {},
                    onSyncPicons = {},
                    onAbout = { about = true },
                    onChangelog = { changelog = true },
                    onBackup = { backup = true },
                )
            }
        }

        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.onNodeWithText("Changelog").performScrollTo().performClick()
        composeRule.onNodeWithText("Backup").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(about)
        assertTrue(changelog)
        assertTrue(backup)
    }
}
