package net.reichholf.dreamdroid.ui.settings

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
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
            InstrumentationRegistry.getInstrumentation().targetContext
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
                    onSyncPicons = {}
                )
            }
        }

        composeRule.onNodeWithText("Video Player").assertIsDisplayed()
        composeRule.onNodeWithText("Integrated video player").assertIsDisplayed()
        composeRule.onNodeWithText("Useability").assertIsDisplayed()
        composeRule.onNodeWithText("Start screen").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Now-playing strip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Day/Night Theme choices").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("MultiEPG text size").performScrollTo().assertIsDisplayed()
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
                    onBackup = { backup = true }
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

    @Test
    fun multiEpgTextSizeDefaultsToComfortableAndStoresCompact() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE)
            .commit()
        val state = SettingsState.create(context)
        composeRule.setContent {
            DreamDroidTheme {
                SettingsScreen(
                    state = state,
                    onThemeChanged = {},
                    onDynamicColorsChanged = {},
                    onSyncPicons = {}
                )
            }
        }

        composeRule.onNodeWithText("MultiEPG text size").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Comfortable").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("MultiEPG text size").performClick()
        composeRule.onNodeWithText("Compact").performClick()
        composeRule.waitForIdle()
        assertEquals(
            "compact",
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString(DreamDroid.PREFS_KEY_MULTIEPG_TEXT_SIZE, null)
        )
        assertEquals("compact", state.multiEpgTextSize)
    }

    @Test
    fun nowPlayingStripDefaultsOnAndStoresOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP)
            .commit()
        val state = SettingsState.create(context)
        assertTrue(state.nowPlayingStrip)
        composeRule.setContent {
            DreamDroidTheme {
                SettingsScreen(
                    state = state,
                    onThemeChanged = {},
                    onDynamicColorsChanged = {},
                    onSyncPicons = {}
                )
            }
        }

        composeRule.onNodeWithText("Now-playing strip").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(
            false,
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_NOW_PLAYING_STRIP, true)
        )
        assertEquals(false, state.nowPlayingStrip)
    }

    @Test
    fun startScreenTitleIsGermanWhenLocaleIsDe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.GERMAN)
        val germanContext = context.createConfigurationContext(config)
        assertEquals("Startbildschirm", germanContext.getString(R.string.start_screen))
        assertEquals(
            "Bildschirm, der beim Öffnen der App angezeigt wird (Hauptziele im Navigationsmenü)",
            germanContext.getString(R.string.start_screen_long)
        )

        val state = SettingsState.create(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides germanContext) {
                DreamDroidTheme {
                    SettingsScreen(
                        state = state,
                        onThemeChanged = {},
                        onDynamicColorsChanged = {},
                        onSyncPicons = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Startbildschirm").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun switchRowsMeetMinHeightAndListDialogRadioRowsMeetMinHeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = SettingsState.create(context)
        composeRule.setContent {
            DreamDroidTheme {
                SettingsScreen(
                    state = state,
                    onThemeChanged = {},
                    onDynamicColorsChanged = {},
                    onSyncPicons = {}
                )
            }
        }

        val switchRow = composeRule.onNode(hasText("Integrated video player") and isToggleable())
            .getBoundsInRoot()
        val switchHeight = switchRow.bottom - switchRow.top
        assertTrue(
            "Switch rows are at least 56.dp, height=$switchHeight",
            switchHeight >= 56.dp
        )

        composeRule.onNodeWithText("MultiEPG text size").performScrollTo().performClick()
        composeRule.waitForIdle()
        val radioRow = composeRule.onNode(hasText("Compact") and isSelectable())
            .getBoundsInRoot()
        val radioHeight = radioRow.bottom - radioRow.top
        assertTrue(
            "List preference radio rows are at least 56.dp, height=$radioHeight",
            radioHeight >= 56.dp
        )
    }
}
