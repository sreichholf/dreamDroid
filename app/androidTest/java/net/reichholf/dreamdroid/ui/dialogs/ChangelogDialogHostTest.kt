package net.reichholf.dreamdroid.ui.dialogs

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.window.DialogProperties
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
 * Changelog hosts as a Material 3 [ChangelogModalSheet] inside a Navigation `dialog`
 * (same host as production [net.reichholf.dreamdroid.ui.nav.PhoneNavHost]).
 */
class ChangelogDialogHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun contentColorIsOnSurfaceInsideNightModalSheet() {
        var localContent = Color.Unspecified
        var onSurface = Color.Unspecified
        composeRule.setContent {
            DreamDroidTheme {
                localContent = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
                ChangelogModalSheet(
                    onDismiss = {},
                    markdown = SAMPLE_CHANGELOG
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CHANGELOG_SHEET_TAG).assertExists()
        composeRule.onNodeWithText("Changelog").assertIsDisplayed()
        composeRule.onNodeWithText("2.0.463").assertIsDisplayed()
        composeRule.onNodeWithText("NEW: MultiEPG — graphical EPG grid").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(onSurface, localContent)
            assertTrue(
                "night onSurface should be light, luminance=${onSurface.luminance()}",
                onSurface.luminance() > 0.5f
            )
        }
    }

    @Test
    fun closeDismissesNavDialogSheet() {
        composeRule.setContent {
            DreamDroidTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") {
                        LaunchedEffect(Unit) {
                            navController.navigate(PhoneNavRoutes.CHANGELOG)
                        }
                    }
                    dialog(
                        PhoneNavRoutes.CHANGELOG,
                        dialogProperties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false,
                            dismissOnClickOutside = false
                        )
                    ) {
                        ChangelogDialog(
                            onDismiss = { navController.popBackStack() },
                            markdown = SAMPLE_CHANGELOG
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Changelog").assertIsDisplayed()
        composeRule.onNodeWithText("IMPORTANT: enable certificates").assertIsDisplayed()
        composeRule.onNodeWithText("Close").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Changelog").assertDoesNotExist()
        composeRule.onNodeWithText("2.0.463").assertDoesNotExist()
    }
}

private const val SAMPLE_CHANGELOG = """
### IMPORTANT: enable certificates
## 2.0.463
* NEW: MultiEPG — graphical EPG grid
* FIX: screenshots
"""
