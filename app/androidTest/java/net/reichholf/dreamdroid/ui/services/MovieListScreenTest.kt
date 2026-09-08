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

class MovieListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsMovieTitleAndService() {
        composeRule.setContent {
            DreamDroidTheme {
                MovieListScreen(
                    items = listOf(
                        MovieListItem(
                            index = 0,
                            title = "Recorded film",
                            serviceName = "ZDF",
                            fileSize = "1.2 GB",
                            time = "01.01.2026",
                            length = "90",
                        ),
                    ),
                    onItemClick = {},
                    onItemLongClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Recorded film").assertIsDisplayed()
        composeRule.onNodeWithText("ZDF").assertIsDisplayed()
    }
}
