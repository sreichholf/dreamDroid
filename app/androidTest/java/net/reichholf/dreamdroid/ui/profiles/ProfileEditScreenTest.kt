package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfileEditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun addModeShowsDefaultsKeyLabelsAndSaveFab() {
        val state = ProfileEditState.fromProfile(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Profile name").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hostname or IP").assertIsDisplayed()
        composeRule.onNodeWithText("443").assertIsDisplayed()
        composeRule.onAllNodesWithText("https", substring = false).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Enable Login").assertIsDisplayed()
        composeRule.onNodeWithText("Streaming").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Port (Live)").assertIsDisplayed()
        composeRule.onNodeWithText("Port (Movies)").assertIsDisplayed()
        composeRule.onNodeWithText("8001").assertIsDisplayed()
        composeRule.onNodeWithText("80").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save").assertIsDisplayed()
        // Login section hidden by default
        composeRule.onNodeWithText("User").assertDoesNotExist()
    }

    @Test
    fun togglingLoginAndEncoderShowsAndHidesSections() {
        val state = ProfileEditState.fromProfile(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("User").assertDoesNotExist()
        composeRule.onNodeWithText("Enable Login").performClick()
        composeRule.onNodeWithText("User").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("root").assertIsDisplayed()

        composeRule.onNodeWithText("Port (Live)").assertIsDisplayed()
        composeRule.onNodeWithText("Use encoder for streaming").performScrollTo().performClick()
        composeRule.onNodeWithText("Port (Live)").assertDoesNotExist()
        composeRule.onNodeWithText("Stream path").assertIsDisplayed()
        composeRule.onNodeWithText("stream").assertIsDisplayed()
        composeRule.onNodeWithText("554").assertIsDisplayed()

        composeRule.onNodeWithText("Encoder user").assertDoesNotExist()
        composeRule.onAllNodesWithText("Enable Login")[1].performClick()
        composeRule.onNodeWithText("Encoder user").assertIsDisplayed()
    }

    @Test
    fun editModeSeedsProfileFields() {
        val profile = Profile.getDefault()
        profile.setName("Living Room")
        profile.setHost("192.168.1.50")
        profile.setPort("8080", false, false)
        profile.setLogin(true)
        profile.setUser("admin")
        val state = ProfileEditState.fromProfile(profile)
        composeRule.setContent {
            DreamDroidTheme {
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("Living Room").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.1.50").assertIsDisplayed()
        composeRule.onNodeWithText("8080").assertIsDisplayed()
        composeRule.onNodeWithText("User").assertIsDisplayed()
        composeRule.onNodeWithText("admin").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Save").assertIsDisplayed()
    }
}
