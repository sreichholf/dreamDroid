package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvProfileCheckScreenTest {
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
            DreamDroidTvTheme {
                TvProfileCheckScreen(
                    gate = TvSessionGate.Checking(UiText.Resource(R.string.checking_connection)),
                    onRecheck = {},
                    onProfiles = {}
                )
            }
        }
        composeRule.onNodeWithTag("tv_profile_check").assertIsDisplayed()
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.checking_connection
            )
        ).assertIsDisplayed()
    }

    @Test
    fun failedShowsRecheckAndProfilesActions() {
        var recheck = false
        var profiles = false
        composeRule.setContent {
            DreamDroidTvTheme {
                TvProfileCheckScreen(
                    gate = TvSessionGate.Failed(
                        title = UiText.Raw("user@host:80"),
                        message = UiText.Resource(R.string.host_unreach)
                    ),
                    onRecheck = { recheck = true },
                    onProfiles = { profiles = true }
                )
            }
        }
        composeRule.onNodeWithTag("tv_profile_check").assertIsDisplayed()
        composeRule.onNodeWithText("user@host:80").assertIsDisplayed()
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.host_unreach
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("tv_profile_check_recheck").assertIsDisplayed()
        composeRule.onNodeWithTag("tv_profile_check_profiles").assertIsDisplayed()

        activateTvSurface("tv_profile_check_recheck") { recheck }
        activateTvSurface("tv_profile_check_profiles") { profiles }
        composeRule.runOnIdle {
            assertTrue(recheck)
            assertTrue(profiles)
        }
    }

    private fun activateTvSurface(tag: String, alreadyFired: () -> Boolean) {
        val node = composeRule.onNodeWithTag(tag)
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        if (!alreadyFired()) {
            node.performClick()
        }
    }
}
