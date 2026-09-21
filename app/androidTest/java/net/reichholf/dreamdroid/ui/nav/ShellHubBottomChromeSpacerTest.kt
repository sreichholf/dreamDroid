package net.reichholf.dreamdroid.ui.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShellHubBottomChromeSpacerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun phoneReservesDestinationBarWithoutNowPlaying() {
        composeRule.setContent {
            DreamDroidTheme {
                ShellHubBottomChromeSpacer(nowPlayingStripEnabled = false)
            }
        }
        composeRule.onNodeWithTag(SHELL_HUB_DESTINATION_BAR_SPACER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SHELL_HUB_NOW_PLAYING_SPACER_TAG).assertDoesNotExist()
    }

    @Test
    fun phoneReservesDestinationBarAndNowPlaying() {
        composeRule.setContent {
            DreamDroidTheme {
                ShellHubBottomChromeSpacer(nowPlayingStripEnabled = true)
            }
        }
        composeRule.onNodeWithTag(SHELL_HUB_DESTINATION_BAR_SPACER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SHELL_HUB_NOW_PLAYING_SPACER_TAG).assertIsDisplayed()
    }

    @Test
    fun tabletSkipsDestinationBarWhenRailIsShowing() {
        composeRule.setContent {
            DreamDroidTheme {
                CompositionLocalProvider(LocalShellUsesDestinationRail provides true) {
                    ShellHubBottomChromeSpacer(nowPlayingStripEnabled = true)
                }
            }
        }
        composeRule.onNodeWithTag(SHELL_HUB_DESTINATION_BAR_SPACER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SHELL_HUB_NOW_PLAYING_SPACER_TAG).assertIsDisplayed()
    }

    @Test
    fun tabletToolsReservesNoBottomChromeWhenRailIsShowing() {
        composeRule.setContent {
            DreamDroidTheme {
                CompositionLocalProvider(LocalShellUsesDestinationRail provides true) {
                    ShellHubBottomChromeSpacer()
                }
            }
        }
        composeRule.onNodeWithTag(SHELL_HUB_DESTINATION_BAR_SPACER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SHELL_HUB_NOW_PLAYING_SPACER_TAG).assertDoesNotExist()
    }
}
