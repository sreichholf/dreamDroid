package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfilesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsDemoRowAndAddProfileFab() {
        composeRule.setContent {
            DreamDroidTheme {
                ProfilesScreen(
                    profiles = listOf(
                        ProfileListItem(
                            id = 1,
                            name = "Demo",
                            host = "dreamdroid.org",
                            active = true,
                        ),
                    ),
                    addLabel = "Add Profile",
                    onProfileClick = {},
                    onProfileLongClick = {},
                    onAddClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Demo").assertIsDisplayed()
        composeRule.onNodeWithText("dreamdroid.org").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add Profile").assertIsDisplayed()
    }
}
