package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfileEditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @After
    fun deleteF05Profiles() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = AppDatabase.profilesBlocking(ctx)
        dao.getProfiles()
            .filter { it.name?.startsWith("f05-") == true }
            .forEach { dao.deleteProfile(it) }
    }

    @Test
    fun addModeShowsDefaultsKeyLabelsAndTvSaveFab() {
        val state = ProfileEditState.fromProfile(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                // Default showSaveFab=true is the TV PreferenceActivity host.
                // Phone ProfileEditDestination passes showSaveFab=false (toolbar Save).
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {}
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
    fun lastFieldsScrollIntoViewWithoutScaffoldFab() {
        val state = ProfileEditState.fromProfile(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                // Phone destination composition: toolbar Save, no in-content FAB.
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {},
                    showSaveFab = false
                )
            }
        }

        composeRule.onNodeWithContentDescription("Save").assertDoesNotExist()
        composeRule.onNodeWithText("Streaming").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Port (Live)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Live").assertIsDisplayed()
        composeRule.onNodeWithText("Movies").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun liveAndMoviesStackFullWidthSwitchRows() {
        val state = ProfileEditState.fromProfile(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {},
                    showSaveFab = false
                )
            }
        }

        composeRule.onNodeWithText("Streaming").performScrollTo()
        composeRule.onNodeWithText("Live").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Movies").performScrollTo().assertIsDisplayed()
        val live = composeRule.onNodeWithText("Live").getBoundsInRoot()
        val movies = composeRule.onNodeWithText("Movies").getBoundsInRoot()
        assertTrue(
            "Movies should stack under Live, live=$live movies=$movies",
            movies.top >= live.bottom
        )
        assertTrue(
            "Live and Movies should share the start edge, live=$live movies=$movies",
            abs((movies.left - live.left).value) < 1f
        )

        composeRule.onAllNodesWithText("Login")[0].performScrollTo()
        composeRule.onAllNodesWithText("Login")[1].performScrollTo()
        val liveLogin = composeRule.onAllNodesWithText("Login")[0].getBoundsInRoot()
        val moviesLogin = composeRule.onAllNodesWithText("Login")[1].getBoundsInRoot()
        val moviesHttps = composeRule.onAllNodesWithText("https")[1]
            .performScrollTo()
            .getBoundsInRoot()
        assertTrue(
            "Movies Login should sit under Live Login, live=$liveLogin movies=$moviesLogin",
            moviesLogin.top >= liveLogin.bottom
        )
        assertTrue(
            "Movies https should sit under Movies Login, login=$moviesLogin https=$moviesHttps",
            moviesHttps.top >= moviesLogin.bottom
        )
        assertTrue(
            "Switch rows are at least 56.dp, height=${moviesHttps.height}",
            moviesHttps.height >= 56.dp
        )
    }

    @Test
    fun togglingLoginAndEncoderShowsAndHidesSections() {
        val state = ProfileEditState.fromProfile(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {}
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
        composeRule.onNodeWithText("Encoder user").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editModeSeedsProfileFields() {
        val profile = Profile.getDefault()
        profile.name = "Living Room"
        profile.host = "192.168.1.50"
        profile.setPort("8080", false, false)
        profile.login = true
        profile.user = "admin"
        val state = ProfileEditState.fromProfile(profile)
        composeRule.setContent {
            DreamDroidTheme {
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {}
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

    @Test
    fun emptyHostSaveDoesNotAddProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = ProfileEditState.fromProfile(Profile.getDefault())
        state.name = "f05-empty-host"
        state.host = ""
        var outcome: ProfilePersistOutcome? = null
        composeRule.setContent {
            DreamDroidTheme {
                ProfileEditScreen(
                    state = state,
                    saveLabel = "Save",
                    onSave = {
                        val profile = Profile.getDefault()
                        state.applyTo(profile)
                        outcome = persistEditedProfile(context, profile)
                    }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Save").performClick()
        assertEquals("The host name cannot be empty!", outcome!!.message)
        assertFalse(outcome!!.saved)
        val saved = AppDatabase.profilesBlocking(context).getProfiles()
            .any { it.name == "f05-empty-host" }
        assertFalse(saved)
    }
}
