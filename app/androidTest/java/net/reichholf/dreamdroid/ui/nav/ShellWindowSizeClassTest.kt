package net.reichholf.dreamdroid.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.services.TvMoviesDestination
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Bar versus rail follows the window size class from [LocalWindowInfo], which
 * [androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2] reads. The activity's
 * own configuration is left alone.
 */
class ShellWindowSizeClassTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .commit()
    }

    @Test
    fun expandedWindowShowsDestinationRail() {
        showShell(width = 900.dp, height = 900.dp)
        composeRule.onNodeWithTag(DESTINATION_RAIL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SHELL_CHROME_TAG).assertDoesNotExist()
    }

    @Test
    fun compactWindowShowsDestinationBar() {
        showShell(width = 400.dp, height = 800.dp)
        composeRule.onNodeWithTag(SHELL_CHROME_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DESTINATION_RAIL_TAG).assertDoesNotExist()
    }

    private fun showShell(width: Dp, height: Dp) {
        val size = with(composeRule.density) {
            IntSize(width.roundToPx(), height.roundToPx())
        }
        composeRule.setContent {
            DreamDroidTheme {
                CompositionLocalProvider(LocalWindowInfo provides FixedWindowInfo(size)) {
                    WindowSizeHost()
                }
            }
        }
        composeRule.waitForIdle()
    }
}

@Composable
private fun WindowSizeHost() {
    val destination = remember {
        ShellDestinationBarController().apply {
            content = ShellDestinationBarContent.TvMovies(
                TvMoviesHubState().apply {
                    selected = TvMoviesDestination.TIMER
                    nowPlayingStripEnabled = false
                }
            )
        }
    }
    PhoneShell(
        drawerListState = remember { DrawerListState() },
        drawerOpen = false,
        onDrawerOpenChange = {},
        profileName = "Living Room",
        connectionLabel = "Online",
        onProfileClick = {},
        onDrawerItemClick = {},
        onNavigationClick = {},
        destinationController = destination,
        fabController = remember { ShellFabController() },
        onToolbarReady = {}
    ) {}
}

private class FixedWindowInfo(override val containerSize: IntSize) : WindowInfo {
    override val isWindowFocused: Boolean = true
}
