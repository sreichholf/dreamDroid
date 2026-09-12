package net.reichholf.dreamdroid.ui.profilecheck

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.nav.navigateAboveProfileCheck
import net.reichholf.dreamdroid.ui.nav.navigateReplacingProfileCheck
import net.reichholf.dreamdroid.ui.nav.navigateToProfileCheck
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * After a successful check, PROFILE_CHECK must leave the back stack.
 * From a failed check, Profiles may keep the gate underneath for Recheck.
 */
class ProfileCheckBackStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun successfulLeaveRemovesProfileCheckFromBackStack() {
        var backStackRoutes = emptyList<String>()
        composeRule.setContent {
            DreamDroidTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = PhoneNavRoutes.HUB,
                ) {
                    composable(PhoneNavRoutes.HUB) { Text("Hub") }
                    composable(PhoneNavRoutes.PROFILE_CHECK) { Text("ProfileCheck") }
                    composable(PhoneNavRoutes.PROFILES) { Text("Profiles") }
                }
                LaunchedEffect(Unit) {
                    navController.navigateToProfileCheck()
                    navController.navigateReplacingProfileCheck(PhoneNavRoutes.HUB)
                    backStackRoutes = navController.currentBackStack.value.mapNotNull { it.destination.route }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Hub").assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse(
                "profile_check must not remain under the service list: $backStackRoutes",
                PhoneNavRoutes.PROFILE_CHECK in backStackRoutes,
            )
            assertEquals(PhoneNavRoutes.HUB, backStackRoutes.last())
        }
    }

    @Test
    fun failedProfilesKeepsProfileCheckUnderneath() {
        var backStackRoutes = emptyList<String>()
        composeRule.setContent {
            DreamDroidTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = PhoneNavRoutes.HUB,
                ) {
                    composable(PhoneNavRoutes.HUB) { Text("Hub") }
                    composable(PhoneNavRoutes.PROFILE_CHECK) { Text("ProfileCheck") }
                    composable(PhoneNavRoutes.PROFILES) { Text("Profiles") }
                }
                LaunchedEffect(Unit) {
                    navController.navigateToProfileCheck()
                    navController.navigateAboveProfileCheck(PhoneNavRoutes.PROFILES)
                    backStackRoutes = navController.currentBackStack.value.mapNotNull { it.destination.route }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Profiles").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(
                "failed-check → Profiles should keep profile_check underneath: $backStackRoutes",
                PhoneNavRoutes.PROFILE_CHECK in backStackRoutes,
            )
            assertEquals(PhoneNavRoutes.PROFILES, backStackRoutes.last())
        }
    }
}
