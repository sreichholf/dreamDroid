package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ChangelogScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsNoticeVersionAndListItemsAsSeparateNodes() {
        composeRule.setContent {
            DreamDroidTheme {
                ChangelogScreen(markdown = SAMPLE_CHANGELOG)
            }
        }
        composeRule.onNodeWithText("IMPORTANT: enable certificates").assertIsDisplayed()
        composeRule.onNodeWithText("2.0.463").assertIsDisplayed()
        composeRule.onNodeWithText("NEW: MultiEPG — graphical EPG grid").assertIsDisplayed()
        composeRule.onNodeWithText("FIX: screenshots").assertIsDisplayed()
    }

    @Test
    fun versionHeadingHasBreathingRoomAboveTheFirstItem() {
        composeRule.setContent {
            DreamDroidTheme {
                ChangelogScreen(markdown = SAMPLE_CHANGELOG)
            }
        }
        val heading = composeRule.onNodeWithText("2.0.463").getBoundsInRoot()
        val firstItem = composeRule
            .onNodeWithText("NEW: MultiEPG — graphical EPG grid")
            .getBoundsInRoot()
        val gap = firstItem.top - heading.bottom
        assertTrue(
            "version heading and first item should be spaced, gap=$gap",
            gap >= 8.dp
        )
    }
}

private const val SAMPLE_CHANGELOG = """
### IMPORTANT: enable certificates
## 2.0.463
* NEW: MultiEPG — graphical EPG grid
* FIX: screenshots
"""
