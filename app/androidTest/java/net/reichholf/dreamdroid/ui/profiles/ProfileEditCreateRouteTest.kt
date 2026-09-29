package net.reichholf.dreamdroid.ui.profiles

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.testutil.memoryProfiles
import net.reichholf.dreamdroid.ui.nav.PhoneNavHostState
import net.reichholf.dreamdroid.ui.nav.ShellViewModel
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A [net.reichholf.dreamdroid.ui.nav.ProfileEdit] route with no saved id is the create
 * form; nothing is loaded from Room.
 */
class ProfileEditCreateRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun routeWithoutIdShowsTheCreateForm() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Application
        val handle = PhoneNavHostState(
            SavedStateHandle(),
            memoryProfiles(),
            SessionConnectionHolder(),
            SettingsRepository(
                PreferenceManager.getDefaultSharedPreferences(
                    InstrumentationRegistry.getInstrumentation().targetContext
                )
            )
        )
        val profiles = memoryProfiles()
        val viewModel = ProfileEditViewModel(SavedStateHandle(), profiles)
        val shellActions = ShellViewModel(
            ReceiverRepository(EnigmaClientFactory(profiles), profiles),
            profiles
        )
        composeRule.setContent {
            DreamDroidTheme {
                ProfileEditDestination(
                    handle = handle,
                    viewModel = viewModel,
                    shellActions = shellActions
                )
            }
        }
        val profileName = app.getString(R.string.profile_name)
        val connection = app.getString(R.string.connection)
        composeRule.onNodeWithText(profileName).assertIsDisplayed()
        composeRule.onNodeWithText(connection).assertIsDisplayed()
    }
}
