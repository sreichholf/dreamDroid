package net.reichholf.dreamdroid.ui.profilecheck

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.testutil.testShellViewModel
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The shell's gate once the active profile is gone: [ProfileCheckUi.NoProfile], and Add
 * Profile asks the shell for the setup assistant instead of navigating on its own.
 */
class ProfileCheckNoProfileShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun losingTheProfileShowsTheGateAndAddProfileAsksForSetup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Application
        val profile = Profile().apply {
            id = 1
            name = "Box"
            host = "127.0.0.1"
            port = 80
        }
        val profiles = memoryProfiles(profile)
        profiles.setCurrent(profile)
        val shell = testShellViewModel(profiles)
        composeRule.setContent {
            DreamDroidTheme {
                val state by shell.uiState.collectAsStateWithLifecycle()
                ProfileCheckScreen(
                    ui = state.profileCheck,
                    onRecheck = shell::recheck,
                    onProfiles = {},
                    onAddProfile = shell::requestSetup
                )
            }
        }

        composeRule.runOnIdle { profiles.clearCurrent() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shell.uiState.value.profileCheck is ProfileCheckUi.NoProfile
        }
        composeRule.onNodeWithText(app.getString(R.string.no_profile_title)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.no_profile_body)).assertIsDisplayed()
        composeRule.onNodeWithText(app.getString(R.string.profile_add)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { shell.uiState.value.setupRequested }
    }
}
