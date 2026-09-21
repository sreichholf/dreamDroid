package net.reichholf.dreamdroid.ui.video

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

class OverlayBouquetBarTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun barShowsBouquetsAndClickSelectsAnother() {
        var clicked: Service? = null
        val favourites = Service("fav", "Favourites")
        val news = Service("news", "News")
        composeRule.setContent {
            DreamDroidTheme(forceDark = true) {
                OverlayBouquetBar(
                    bouquets = listOf(favourites, news),
                    selectedRef = favourites.reference,
                    onBouquetClick = { clicked = it }
                )
            }
        }

        composeRule.onNodeWithTag(OVERLAY_BOUQUET_BAR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Favourites").assertIsSelected()
        composeRule.onNodeWithText("News").assertIsNotSelected().performClick()
        assertEquals(news, clicked)
    }

    @Test
    fun emptyListDrawsNoBar() {
        composeRule.setContent {
            DreamDroidTheme(forceDark = true) {
                OverlayBouquetBar(
                    bouquets = emptyList(),
                    selectedRef = null,
                    onBouquetClick = {}
                )
            }
        }
        composeRule.onNodeWithTag(OVERLAY_BOUQUET_BAR_TAG).assertDoesNotExist()
    }
}
