package net.reichholf.dreamdroid.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.After
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

    @After
    fun restoreIntegratedPlayer() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().remove(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER).commit()
    }

    @Test
    fun tvPreferenceTitlesVisible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = SettingsState.create(context)
        composeRule.setContent {
            DreamDroidTheme {
                TvSettingsScreen(state = state)
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
    fun integratedPlayerDefaultsOnAndStoresOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER)
            .commit()
        val state = SettingsState.create(context)
        assertTrue(state.integratedVideoPlayer)
        composeRule.setContent {
            DreamDroidTheme {
                TvSettingsScreen(state = state)
            }
        }

        composeRule.onNode(hasText("Integrated video player") and isToggleable()).performClick()
        composeRule.waitForIdle()
        assertEquals(
            false,
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, true)
        )
        assertFalse(state.integratedVideoPlayer)
    }

    @Test
    fun hwAccelDisabledWhenIntegratedPlayerOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(DreamDroid.PREFS_KEY_INTEGRATED_PLAYER, false)
            .commit()
        val state = SettingsState.create(context)
        composeRule.setContent {
            DreamDroidTheme {
                TvSettingsScreen(state = state)
            }
        }

        composeRule.onNodeWithText("Accelerated decoding").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Hardware Acceleration").assertCountEquals(0)
    }
}
