package net.reichholf.dreamdroid.ui.profiles

import android.app.Application
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.Hub
import net.reichholf.dreamdroid.ui.nav.PhoneNavHostState
import net.reichholf.dreamdroid.ui.nav.ProfileEdit
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * A [ProfileEdit] with no saved id is the create form. The profile is not loaded
 * from Room.
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
    fun nullProfileIdShowsTheCreateForm() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as Application
        val handle = PhoneNavHostState(app, SavedStateHandle())
        composeRule.setContent {
            DreamDroidTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Hub) {
                    composable<Hub> { }
                    composable<ProfileEdit> { entry ->
                        ProfileEditDestination(
                            handle = handle,
                            route = entry.toRoute()
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    navController.navigate(ProfileEdit())
                }
            }
        }
        val profileName = app.getString(R.string.profile_name)
        val connection = app.getString(R.string.connection)
        composeRule.onNodeWithText(profileName).assertIsDisplayed()
        composeRule.onNodeWithText(connection).assertIsDisplayed()
    }
}
