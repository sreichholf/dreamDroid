package net.reichholf.dreamdroid.ui.dialogs

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2.1g-ii-c: drawer modals are Navigation Compose `dialog`s (SleepTimer proof here).
 * Profile-check failure uses a shell-owned Material 3 AlertDialog.
 */
class DrawerDialogHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun sleepTimerContentColorIsOnSurfaceInsideNavDialog() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home",
                ) {
                    composable("home") {
                        LaunchedEffect(Unit) {
                            navController.navigate(PhoneNavRoutes.SLEEP_TIMER)
                        }
                    }
                    dialog(PhoneNavRoutes.SLEEP_TIMER) {
                        localContent = LocalContentColor.current
                        onSurface = MaterialTheme.colorScheme.onSurface
                        SleepTimerDialog(
                            initialMinutes = 45,
                            initialEnabled = true,
                            initialAction = SleepTimer.ACTION_STANDBY,
                            onDismiss = { navController.popBackStack() },
                            onSave = { _, _, _ -> },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Activate").assertIsDisplayed()
        composeRule.onNodeWithText("Standby").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }

    @Test
    fun profileCheckFailedDialogShowsRecheckAndProfiles() {
        composeRule.setContent {
            DreamDroidTheme {
                ProfileCheckFailedDialog(
                    title = "user@box:80",
                    message = "Cannot reach box",
                    onRecheck = {},
                    onProfiles = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cannot reach box").assertIsDisplayed()
        composeRule.onNodeWithText("Recheck").assertIsDisplayed()
        composeRule.onNodeWithText("Profiles").assertIsDisplayed()
    }
}
