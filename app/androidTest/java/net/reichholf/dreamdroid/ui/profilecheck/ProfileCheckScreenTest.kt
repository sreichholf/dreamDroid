package net.reichholf.dreamdroid.ui.profilecheck

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfileCheckScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun checkingShowsProgressMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                ProfileCheckScreen(
                    ui = ProfileCheckUi.Checking(UiText.Raw("Checking connection…")),
                    onRecheck = {},
                    onProfiles = {},
                    onAddProfile = {}
                )
            }
        }
        composeRule.onNodeWithText("Checking connection…").assertIsDisplayed()
    }

    @Test
    fun failedShowsMessageAndActions() {
        var recheck = false
        var profiles = false
        composeRule.setContent {
            DreamDroidTheme {
                ProfileCheckScreen(
                    ui = ProfileCheckUi.Failed(
                        title = UiText.Raw("user@host:80"),
                        message = UiText.Raw("Host unreachable")
                    ),
                    onRecheck = { recheck = true },
                    onProfiles = { profiles = true },
                    onAddProfile = {}
                )
            }
        }
        composeRule.onNodeWithText("user@host:80").assertIsDisplayed()
        composeRule.onNodeWithText("Host unreachable").assertIsDisplayed()
        composeRule.onNodeWithText("Recheck").assertIsDisplayed()
        composeRule.onNodeWithText("Profiles").assertIsDisplayed()
        composeRule.onNodeWithText("Recheck").performClick()
        composeRule.onNodeWithText("Profiles").performClick()
        composeRule.runOnIdle {
            assertTrue(recheck)
            assertTrue(profiles)
        }
    }

    @Test
    fun noProfileShowsCopyAndAddProfile() {
        var addProfile = false
        composeRule.setContent {
            DreamDroidTheme {
                ProfileCheckScreen(
                    ui = ProfileCheckUi.NoProfile,
                    onRecheck = {},
                    onProfiles = {},
                    onAddProfile = { addProfile = true }
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.no_profile_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.no_profile_body)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.profile_add))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(addProfile) }
    }
}
