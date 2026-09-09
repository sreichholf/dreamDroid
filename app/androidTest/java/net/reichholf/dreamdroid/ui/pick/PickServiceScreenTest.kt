package net.reichholf.dreamdroid.ui.pick

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PickServiceScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun seededBouquetsShowNamesAndClick() {
        val ars = Service("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\" ORDER BY bouquet", "Favourites (TV)")
        val radio = Service("1:7:2:0:0:0:0:0:0:0:FROM BOUQUET \"bouquets.radio\" ORDER BY bouquet", "All Radio")
        var clicked: Service? = null
        composeRule.setContent {
            DreamDroidTheme {
                PickServiceScreen(
                    items = listOf(ars, radio),
                    onItemClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Favourites (TV)").assertIsDisplayed()
        composeRule.onNodeWithText("All Radio").assertIsDisplayed().performClick()
        assertEquals(radio, clicked)
    }

    @Test
    fun emptyStateShowsMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                PickServiceScreen(
                    items = emptyList(),
                    onItemClick = {},
                    emptyMessage = "No items to display…",
                )
            }
        }
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
    }
}
