package net.reichholf.dreamdroid.ui.zap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ZapScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun seededServicesShowNames() {
        composeRule.setContent {
            DreamDroidTheme {
                ZapScreen(
                    items = listOf(
                        Service("1:0:1:1:1:1:1:0:0:0:", "ARD HD"),
                        Service("1:0:1:2:1:1:1:0:0:0:", "ZDF HD"),
                    ),
                    onItemClick = {},
                    onItemLongClick = {},
                )
            }
        }
        composeRule.onNodeWithText("ARD HD").assertIsDisplayed()
        composeRule.onNodeWithText("ZDF HD").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                ZapScreen(
                    items = emptyList(),
                    onItemClick = {},
                    onItemLongClick = {},
                    emptyMessage = "No items to display…",
                )
            }
        }
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
    }
}
