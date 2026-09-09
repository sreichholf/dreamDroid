package net.reichholf.dreamdroid.ui.share

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShareProfilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun seededProfilesShowNameHostAndClick() {
        val first = ProfileListItem(id = 1, name = "Living Room", host = "dm7080.local", active = false)
        val second = ProfileListItem(id = 2, name = "Bedroom", host = "192.168.1.50", active = false)
        var clicked: ProfileListItem? = null
        composeRule.setContent {
            DreamDroidTheme {
                ShareProfilesScreen(
                    profiles = listOf(first, second),
                    onProfileClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Living Room").assertIsDisplayed()
        composeRule.onNodeWithText("dm7080.local").assertIsDisplayed()
        composeRule.onNodeWithText("Bedroom").assertIsDisplayed().performClick()
        assertEquals(second, clicked)
    }
}
