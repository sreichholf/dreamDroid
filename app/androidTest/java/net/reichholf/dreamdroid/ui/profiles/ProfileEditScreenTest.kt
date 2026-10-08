package net.reichholf.dreamdroid.ui.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.StreamMode
import net.reichholf.dreamdroid.enigma.VpsMode
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

    @Test
    fun addModeShowsDefaultsKeyLabelsAndTvSaveFab() {
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                // Default showSaveFab=true is the TV profiles destination.
                // Phone ProfileEditDestination passes showSaveFab=false (toolbar Save).
                EditableScreen()
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
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                // Phone destination composition: toolbar Save, no in-content FAB.
                EditableScreen(showSaveFab = false)
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
        show(Profile.getDefault())
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
                        EditableScreen(showSaveFab = false)
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
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen(showSaveFab = false)
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
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen()
            }
        }

        composeRule.onNodeWithText("User").assertDoesNotExist()
        composeRule.onNodeWithText("Enable Login").performClick()
        composeRule.onNodeWithText("User").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Show password").assertIsDisplayed()
        composeRule.onNodeWithText("root").assertIsDisplayed()

        composeRule.onNodeWithText("Port (Live)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stream mode").performScrollTo().performClick()
        composeRule.onNodeWithText("Dreambox encoder (RTSP)").performClick()
        composeRule.onNodeWithText("Port (Live)").assertDoesNotExist()
        composeRule.onNodeWithText("Stream path").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("stream").assertIsDisplayed()
        composeRule.onNodeWithText("554").assertIsDisplayed()

        composeRule.onNodeWithText("Encoder user").assertDoesNotExist()
        composeRule.onAllNodesWithText("Enable Login")[1].performClick()
        composeRule.onNodeWithText("Encoder user").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun transcodingModeShowsItsPortAndSavesTheMode() {
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen()
            }
        }

        composeRule.onNodeWithText("Transcoding port").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Stream mode").performScrollTo().performClick()
        composeRule.onNodeWithText("Transcoding (HTTP)").performClick()
        composeRule.onNodeWithText("Transcoding port").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("8002").assertIsDisplayed()
        composeRule.onNodeWithText("Port (Live)").assertDoesNotExist()
        composeRule.onNodeWithText("Stream path").assertDoesNotExist()
        // The web interface's https, then the transcoder's.
        composeRule.onAllNodes(hasText("https") and isToggleable())[1]
            .performScrollTo()
            .assertIsOff()
            .performClick()

        val saved = Profile.getDefault()
        composeRule.runOnIdle { fields.applyTo(saved, form) }
        assertEquals(StreamMode.Transcoding, saved.streamMode)
        assertEquals(8002, saved.transcodePort)
        assertTrue(saved.streamSsl)
    }

    @Test
    fun liveHttpsStartsOffAndRoundTripsThroughTheProfile() {
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen(showSaveFab = false)
            }
        }

        // The web interface's https, Live's, then Movies'.
        val httpsSwitches = composeRule.onAllNodes(hasText("https") and isToggleable())
        httpsSwitches.assertCountEquals(3)
        httpsSwitches[1].performScrollTo().assertIsOff().performClick()
        httpsSwitches[1].assertIsOn()
        httpsSwitches[2].performScrollTo().assertIsOff()
        assertTrue(form.streamSsl)
        assertFalse(form.fileSsl)

        val profile = Profile.getDefault()
        composeRule.runOnIdle { fields.applyTo(profile, form) }
        assertTrue(profile.streamSsl)
        assertFalse(profile.fileSsl)
        assertTrue(ProfileForm.from(profile).streamSsl)
    }

    @Test
    fun editModeSeedsProfileFields() {
        val profile = Profile.getDefault()
        profile.name = "Living Room"
        profile.host = "192.168.1.50"
        profile.setPort("8080", false, false)
        profile.login = true
        profile.user = "admin"
        show(profile)
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen()
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
    fun typingGoesIntoTheOwnedTextFieldState() {
        show(Profile.getDefault().apply { host = "" })
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen()
            }
        }

        composeRule.onNodeWithContentDescription("Hostname or IP").performTextInput("10.0.0.5")

        composeRule.runOnIdle { assertEquals("10.0.0.5", fields.host.text) }
    }

    @Test
    fun hostErrorShowsUnderTheHostField() {
        show(Profile.getDefault().apply { host = "" })
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen(hostError = "The host name cannot be empty!")
            }
        }

        composeRule.onNodeWithText("The host name cannot be empty!").assertIsDisplayed()
    }

    @Test
    fun enablingAllCertificatesShowsWarningAndCancelLeavesOff() {
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen()
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
        assertFalse(form.trustAllCerts)
    }

    @Test
    fun enablingAllCertificatesConfirmTurnsSwitchOn() {
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen()
            }
        }

        composeRule.onNodeWithText("All certificates").performClick()
        composeRule.onNodeWithText("Enable").performClick()
        composeRule.onNodeWithText("Trust all certificates?").assertDoesNotExist()
        composeRule.onNodeWithText("All certificates").assertIsOn()
        assertTrue(form.trustAllCerts)
    }

    @Test
    fun zapAndStreamStartsOffAndRoundTripsThroughTheProfile() {
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen(showSaveFab = false)
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
        assertTrue(form.zapAndStream)

        val profile = Profile.getDefault()
        fields.applyTo(profile, form)
        assertTrue(profile.zapAndStream)
        val reloaded = ProfileForm.from(profile)
        assertTrue(reloaded.zapAndStream)
    }

    @Test
    fun vpsDefaultStartsAtNoAndRoundTripsThroughTheProfile() {
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen(showSaveFab = false)
            }
        }

        val field = composeRule.onNodeWithContentDescription("VPS default for new timers")
        field.performScrollTo().assertIsDisplayed().assertTextContains("No")
        composeRule.onNodeWithText("Used when the receiver has the VPS plugin.")
            .assertIsDisplayed()
        field.performClick()
        composeRule.onNodeWithText("Yes (safe mode)").performClick()

        field.assertTextContains("Yes (safe mode)")
        assertEquals(VpsMode.Safe, form.vpsDefault)
        val profile = Profile.getDefault()
        fields.applyTo(profile, form)
        assertEquals(VpsMode.Safe, profile.vpsDefault)
    }

    @Test
    fun editModeShowsTheSavedVpsDefault() {
        show(Profile.getDefault().apply { vpsDefault = VpsMode.Overwrite })
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen(showSaveFab = false)
            }
        }

        composeRule.onNodeWithContentDescription("VPS default for new timers")
            .performScrollTo()
            .assertTextContains("Yes")
        composeRule.onNodeWithText("Yes (safe mode)").assertDoesNotExist()
    }

    @Test
    fun disablingAllCertificatesDoesNotShowWarning() {
        val profile = Profile.getDefault()
        profile.allCertsTrusted = true
        show(profile)
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen()
            }
        }

        composeRule.onNodeWithText("All certificates").assertIsOn()
        composeRule.onNodeWithText("Trust all certificates?").assertDoesNotExist()
        composeRule.onNodeWithText("All certificates").performClick()
        composeRule.onNodeWithText("Trust all certificates?").assertDoesNotExist()
        composeRule.onNodeWithText("All certificates").assertIsOff()
        assertFalse(form.trustAllCerts)
    }

    @Test
    fun onlinePiconPathAndNamingAreEditableOnlyWhileOnlinePiconsAreOn() {
        show(Profile.getDefault())
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen(showSaveFab = false)
            }
        }

        val online = composeRule.onNodeWithText(string(R.string.online_picons))
        val path = composeRule.onNodeWithContentDescription(string(R.string.sync_picons_path))
        val useName = composeRule.onNodeWithText(string(R.string.picons_online_use_name))
        online.performScrollTo().assertIsOff()
        path.performScrollTo().assertIsNotEnabled()
        useName.performScrollTo().assertIsNotEnabled()

        online.performScrollTo().performClick()
        online.assertIsOn()
        path.performScrollTo().assertIsEnabled()
        useName.performScrollTo().assertIsEnabled().performClick()
        useName.assertIsOn()
    }

    @Test
    fun onlinePiconSettingsRoundTripThroughTheProfile() {
        show(
            Profile.getDefault().apply {
                piconsOnline = true
                piconsOnlineUseName = true
                piconsOnlinePath = "/media/hdd/picon"
            }
        )
        composeRule.setContent {
            DreamDroidTheme {
                EditableScreen(showSaveFab = false)
            }
        }

        composeRule.onNodeWithText(string(R.string.online_picons)).performScrollTo().assertIsOn()
        composeRule.onNodeWithText("/media/hdd/picon").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.picons_online_use_name)).performScrollTo()
            .assertIsOn()

        val profile = Profile.getDefault()
        composeRule.runOnIdle { fields.applyTo(profile, form) }
        assertTrue(profile.piconsOnline)
        assertTrue(profile.piconsOnlineUseName)
        assertEquals("/media/hdd/picon", profile.piconsOnlinePath)
    }

    private val scope = MainScope()
    private val fields = ProfileTextFields(scope)
    private var form by mutableStateOf(ProfileForm())

    @After
    fun cancelFields() {
        scope.cancel()
    }

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(profile: Profile) {
        form = ProfileForm.from(profile)
        fields.fill(profile)
    }

    /** The screen with its state hoisted here, as a ViewModel would hold it. */
    @Composable
    private fun EditableScreen(showSaveFab: Boolean = true, hostError: String? = null) {
        ProfileEditScreen(
            form = form,
            fields = fields,
            hostError = hostError,
            onFormChange = { form = it },
            onSslChange = {
                form = form.copy(ssl = it)
                fields.onSslChanged(it)
            },
            saveLabel = "Save",
            onSave = {},
            showSaveFab = showSaveFab
        )
    }
}
