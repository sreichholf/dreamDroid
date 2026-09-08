package net.reichholf.dreamdroid.ui.services

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TvMoviesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
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
                    onRowSelected = {},
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
}
