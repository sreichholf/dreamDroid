package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import net.reichholf.dreamdroid.ui.nav.phoneNavDestinationViewport
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
        composeRule.onNodeWithText("Port (Live)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Port (Movies)").performScrollTo().assertIsDisplayed()
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
    fun lastMoviesSwitchClearsHostBottomInsetWithoutScaffoldFab() {
        val state = ProfileEditState.fromProfile(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                Box(Modifier.fillMaxSize().testTag("host")) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .phoneNavDestinationViewport(
                                shellBarVisible = false,
                                bottomInset = 48.dp
                            )
                    ) {
                        ProfileEditScreen(
                            state = state,
                            saveLabel = "Save",
                            onSave = {},
                            showSaveFab = false
                        )
                    }
                }
            }
        }

        val moviesHttps = composeRule.onAllNodes(hasText("https") and isToggleable())
            .onLast()
            .performScrollTo()
            .getBoundsInRoot()
        val host = composeRule.onNodeWithTag("host").getBoundsInRoot()
        assertTrue(
            "Last Movies switch must clear the host bottom inset, " +
                "host=$host switch=$moviesHttps",
            moviesHttps.bottom <= host.bottom - 48.dp + 1.5.dp
        )
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

        // Scroll the last Movies control into view, then measure without further
        // scrolling so stacked switch rows keep comparable root bounds.
        val moviesHttpsNode = composeRule.onAllNodes(hasText("https") and isToggleable())
            .onLast()
        moviesHttpsNode.performScrollTo()
        composeRule.waitForIdle()
        val liveLogin = composeRule.onAllNodes(hasText("Login") and isToggleable())
            .onFirst()
            .getBoundsInRoot()
        val moviesLogin = composeRule.onAllNodes(hasText("Login") and isToggleable())
            .onLast()
            .getBoundsInRoot()
        val moviesHttps = moviesHttpsNode.getBoundsInRoot()
        assertTrue(
            "Movies Login should sit under Live Login, live=$liveLogin movies=$moviesLogin",
            moviesLogin.top >= liveLogin.bottom
        )
        assertTrue(
            "Movies https should sit under Movies Login, login=$moviesLogin https=$moviesHttps",
            moviesHttps.top >= moviesLogin.bottom
        )
        val moviesHttpsHeight = moviesHttps.bottom - moviesHttps.top
        assertTrue(
            "Switch rows are at least 56.dp, height=$moviesHttpsHeight",
            moviesHttpsHeight >= 56.dp
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
        composeRule.onNodeWithContentDescription("Show password").assertIsDisplayed()
        composeRule.onNodeWithText("root").assertIsDisplayed()

        composeRule.onNodeWithText("Port (Live)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Use encoder for streaming").performScrollTo().performClick()
        composeRule.onNodeWithText("Port (Live)").assertDoesNotExist()
        composeRule.onNodeWithText("Stream path").performScrollTo().assertIsDisplayed()
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
                        val persisted = persistEditedProfile(context, profile)
                        outcome = persisted
                        if (!persisted.saved) {
                            state.hostError = context.getString(
                                net.reichholf.dreamdroid.R.string.host_empty
                            )
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Save").performClick()
        composeRule.waitForIdle()
        val result = requireNotNull(outcome)
        assertEquals("The host name cannot be empty!", result.message)
        assertFalse(result.saved)
        composeRule.onNodeWithText("The host name cannot be empty!").assertIsDisplayed()
        val saved = AppDatabase.profilesBlocking(context).getProfiles()
            .any { it.name == "f05-empty-host" }
        assertFalse(saved)
    }

    @Test
    fun enablingAllCertificatesShowsWarningAndCancelLeavesOff() {
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

        composeRule.onNodeWithText("All certificates").assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithText("All certificates").performClick()
        composeRule.onNodeWithText("Trust all certificates?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "dreamDroid will not verify this profile's HTTPS certificate. " +
                "Use this only for self-signed certificates on your own receiver. " +
                "This can be dangerous."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Trust all certificates?").assertDoesNotExist()
        composeRule.onNodeWithText("All certificates").assertIsOff()
        assertFalse(state.trustAllCerts)
    }

    @Test
    fun enablingAllCertificatesConfirmTurnsSwitchOn() {
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

        composeRule.onNodeWithText("All certificates").performClick()
        composeRule.onNodeWithText("Enable").performClick()
        composeRule.onNodeWithText("Trust all certificates?").assertDoesNotExist()
        composeRule.onNodeWithText("All certificates").assertIsOn()
        assertTrue(state.trustAllCerts)
    }

    @Test
    fun zapAndStreamStartsOffAndRoundTripsThroughTheProfile() {
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

        val toggle = composeRule.onNodeWithText("Zap and stream")
        toggle.performScrollTo().assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithText(
            "Tune the receiver to this service before playback. A single-tuner box can " +
                "only stream a service on the current transponder, and this also changes " +
                "the channel on the TV."
        ).performScrollTo().assertIsDisplayed()
        toggle.performClick()
        composeRule.onNodeWithText("Zap and stream").assertIsOn()
        assertTrue(state.zapAndStream)

        val profile = Profile.getDefault()
        state.applyTo(profile)
        assertTrue(profile.zapAndStream)
        val reloaded = ProfileEditState.fromProfile(profile)
        assertTrue(reloaded.zapAndStream)
    }

    @Test
    fun disablingAllCertificatesDoesNotShowWarning() {
        val profile = Profile.getDefault()
        profile.allCertsTrusted = true
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

        composeRule.onNodeWithText("All certificates").assertIsOn()
        composeRule.onNodeWithText("Trust all certificates?").assertDoesNotExist()
        composeRule.onNodeWithText("All certificates").performClick()
        composeRule.onNodeWithText("Trust all certificates?").assertDoesNotExist()
        composeRule.onNodeWithText("All certificates").assertIsOff()
        assertFalse(state.trustAllCerts)
    }
}
