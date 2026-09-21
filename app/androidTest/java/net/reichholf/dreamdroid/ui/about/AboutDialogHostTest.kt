package net.reichholf.dreamdroid.ui.about

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
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
            InstrumentationRegistry.getInstrumentation().targetContext
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
                    startDestination = "home"
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
        assertLicensesLeftOfCloseOnSameRow()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f
            )
        }
    }

    @Test
    fun licensesReplacesAboutInsteadOfStacking() {
        composeRule.setContent {
            DreamDroidTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        LaunchedEffect(Unit) {
                            navController.navigate(PhoneNavRoutes.ABOUT)
                        }
                    }
                    dialog(PhoneNavRoutes.ABOUT) {
                        AboutDialog(onDismiss = { navController.popBackStack() })
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("About").assertIsDisplayed()
        composeRule.onNodeWithText("Licenses").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("About").assertDoesNotExist()
        composeRule.onNodeWithText("AndroidX").assertIsDisplayed()
        composeRule.onNodeWithText("Licenses").assertIsDisplayed()
    }

    private fun assertLicensesLeftOfCloseOnSameRow() {
        val licenses = composeRule.onNodeWithText("Licenses").getBoundsInRoot()
        val close = composeRule.onNodeWithText("Close").getBoundsInRoot()
        assertTrue(
            "Licenses should sit left of Close, licenses=$licenses close=$close",
            licenses.right <= close.left
        )
        val licensesCenterY = (licenses.top + licenses.bottom) / 2
        val closeCenterY = (close.top + close.bottom) / 2
        val verticalDelta = licensesCenterY - closeCenterY
        assertTrue(
            "Licenses and Close should share a row, delta=$verticalDelta",
            verticalDelta <= 8.dp && verticalDelta >= (-8).dp
        )
    }
}
