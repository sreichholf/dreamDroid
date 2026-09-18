package net.reichholf.dreamdroid.ui.services

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.current.NOW_PLAYING_STRIP_DIVIDER_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TvMoviesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsDestinationsAndFakeBouquets() {
        composeRule.setContent {
            DreamDroidTheme {
                TvMoviesScreen(
                    selected = TvMoviesDestination.TV,
                    rows = listOf("Favourites (TV)", "All Services"),
                    selectedRow = 0,
                    error = null,
                    onDestinationSelected = {},
                    onRowSelected = {}
                )
            }
        }
        composeRule.onNodeWithText("TV").assertIsDisplayed()
        composeRule.onNodeWithText("Radio").assertIsDisplayed()
        composeRule.onNodeWithText("Movies").assertIsDisplayed()
        composeRule.onNodeWithText("Timer").assertIsDisplayed()
        composeRule.onNodeWithText("Favourites (TV)").assertIsDisplayed()
        composeRule.onNodeWithText("All Services").assertIsDisplayed()
    }

    @Test
    fun nowStripSitsAboveDestinationBar() {
        val state = TvMoviesHubState().apply {
            nowPlayingHeadline = "Das Erste HD · Tagesschau"
        }
        composeRule.setContent {
            DreamDroidTheme {
                TvMoviesShellChrome(state = state)
            }
        }
        composeRule.onNodeWithText("Now").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD · Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithTag(NOW_PLAYING_STRIP_DIVIDER_TAG).assertExists()
        composeRule.onNodeWithText("TV").assertIsDisplayed()
        composeRule.onNodeWithText("Radio").assertIsDisplayed()
        composeRule.onNodeWithText("Movies").assertIsDisplayed()
        composeRule.onNodeWithText("Timer").assertIsDisplayed()
    }

    @Test
    fun nowStripShowsConnectionOffline() {
        val state = TvMoviesHubState().apply {
            nowPlayingLabel = "Connection"
            nowPlayingHeadline = "Offline"
        }
        composeRule.setContent {
            DreamDroidTheme {
                TvMoviesShellChrome(state = state)
            }
        }
        composeRule.onNodeWithText("Connection").assertIsDisplayed()
        composeRule.onNodeWithText("Offline").assertIsDisplayed()
        composeRule.onNodeWithText("Now").assertDoesNotExist()
        composeRule.onNodeWithText("Not available").assertDoesNotExist()
    }

    @Test
    fun hidesNowStripWhenDisabled() {
        val state = TvMoviesHubState().apply {
            nowPlayingStripEnabled = false
            nowPlayingHeadline = "Das Erste HD · Tagesschau"
        }
        composeRule.setContent {
            DreamDroidTheme {
                TvMoviesShellChrome(state = state)
            }
        }
        composeRule.onNodeWithText("Now").assertDoesNotExist()
        composeRule.onNodeWithText("Das Erste HD · Tagesschau").assertDoesNotExist()
        composeRule.onNodeWithTag(NOW_PLAYING_STRIP_DIVIDER_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("TV").assertIsDisplayed()
        composeRule.onNodeWithText("Radio").assertIsDisplayed()
        composeRule.onNodeWithText("Movies").assertIsDisplayed()
        composeRule.onNodeWithText("Timer").assertIsDisplayed()
    }

    @Test
    fun reselectingActiveBouquetTabReportsSameIndex() {
        val selected = mutableListOf<Int>()
        composeRule.setContent {
            DreamDroidTheme {
                TvMoviesHeader(
                    rows = listOf("Favourites (TV)", "Provider"),
                    selectedRow = 1,
                    error = null,
                    onRowSelected = { selected += it }
                )
            }
        }
        composeRule.onNodeWithText("Provider").performClick()
        assertEquals(listOf(1), selected)
    }
}
