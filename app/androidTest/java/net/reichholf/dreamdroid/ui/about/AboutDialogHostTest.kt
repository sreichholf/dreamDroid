package net.reichholf.dreamdroid.ui.about

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
import net.reichholf.dreamdroid.ui.nav.PhoneNavRoutes
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2.1g-ii-b: About is a Navigation Compose `dialog`, not a DialogFragment /
 * MaterialAlertDialogBuilder ComposeView host. Host the dialog the same way production does.
 */
class AboutDialogHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun contentColorIsOnSurfaceInsideNavDialog() {
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
                            navController.navigate(PhoneNavRoutes.ABOUT)
                        }
                    }
                    dialog(PhoneNavRoutes.ABOUT) {
                        localContent = LocalContentColor.current
                        onSurface = MaterialTheme.colorScheme.onSurface
                        AboutDialog(onDismiss = { navController.popBackStack() })
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Licenses").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f,
            )
        }
    }
}
