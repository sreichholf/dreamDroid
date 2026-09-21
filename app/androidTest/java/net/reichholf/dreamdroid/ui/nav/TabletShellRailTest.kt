package net.reichholf.dreamdroid.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.services.TvMoviesDestination
import net.reichholf.dreamdroid.ui.services.TvMoviesDestinationRail
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TabletShellRailTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun railShowsTvRadioMoviesTimerAndReportsSelection() {
        val selected = mutableListOf<TvMoviesDestination>()
        composeRule.setContent {
            DreamDroidTheme {
                TvMoviesDestinationRail(
                    selected = TvMoviesDestination.TV,
                    onDestinationSelected = { selected += it }
                )
            }
        }
        composeRule.onNodeWithTag(DESTINATION_RAIL_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("TV").assertIsDisplayed()
        composeRule.onNodeWithText("Radio").assertIsDisplayed()
        composeRule.onNodeWithText("Movies").assertIsDisplayed()
        composeRule.onNodeWithText("Timer").assertIsDisplayed()
        composeRule.onNodeWithText("TV").assertIsSelected()
        composeRule.onNodeWithText("Movies").performClick()
        assertEquals(listOf(TvMoviesDestination.MOVIES), selected)
    }
}
