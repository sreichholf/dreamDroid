package net.reichholf.dreamdroid.ui.nav

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [PhoneShell] hosts [net.reichholf.dreamdroid.ui.drawer.DrawerScreen] under the
 * profile header. The destinations column must stay on screen in that host;
 * a full-sheet [DrawerScreen] without remaining height clips TV & Movies.
 */
class PhoneShellDrawerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun openDrawerShowsDestinationsBelowBoxActions() {
        composeRule.setContent {
            val destination = remember { ShellDestinationBarController() }
            val fab = remember { ShellFabController() }
            DreamDroidTheme {
                PhoneShell(
                    drawerListState = remember { DrawerListState() },
                    drawerOpen = true,
                    onDrawerOpenChange = {},
                    profileName = "Living Room",
                    connectionLabel = "Online",
                    onProfileClick = {},
                    onDrawerItemClick = {},
                    onNavigationClick = {},
                    destinationController = destination,
                    fabController = fab,
                    onToolbarReady = {},
                    usesRail = false
                ) {}
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Power Control").assertIsDisplayed()
        composeRule.onNodeWithText("Sleep Timer").assertIsDisplayed()
        composeRule.onNodeWithText("Send Message").assertIsDisplayed()
        composeRule.onNodeWithText("TV & Movies").assertIsDisplayed()
        composeRule.onNodeWithText("EPG").assertIsDisplayed()
        composeRule.onNodeWithText("Virtual Remote").assertIsDisplayed()
        composeRule.onNodeWithText("Zap").assertIsDisplayed()
        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
