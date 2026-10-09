package net.reichholf.dreamdroid.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.services.TvMoviesDestination
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Both window sizes show the bottom destination bar; from medium width on its items put the
 * icon beside the label. The size class comes from [LocalWindowInfo], which
 * [androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2] reads. The activity's own
 * configuration is left alone.
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
    fun expandedWindowPutsIconsBesideLabels() {
        showShell(width = 900.dp, height = 900.dp)
        composeRule.onNodeWithTag(SHELL_CHROME_TAG).assertIsDisplayed()
        val offset = labelOffsetFromItemCenter()
        assertTrue("label centered in the item, off by $offset", abs(offset) < 2f)
    }

    @Test
    fun compactWindowPutsIconsAboveLabels() {
        showShell(width = 400.dp, height = 800.dp)
        composeRule.onNodeWithTag(SHELL_CHROME_TAG).assertIsDisplayed()
        val offset = labelOffsetFromItemCenter()
        assertTrue("label below the icon, off by $offset", offset > 4f)
    }

    /** How far, in dp, the selected item's label sits below the middle of the item. */
    private fun labelOffsetFromItemCenter(): Float {
        val item = composeRule.onNodeWithText("Timer").fetchSemanticsNode().boundsInRoot
        val label = composeRule.onNodeWithText("Timer", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        return with(composeRule.density) { (label.center.y - item.center.y).toDp().value }
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
        boxActionsBlocked = false,
        onProfileClick = {},
        onDrawerItemClick = {},
        onNavigationClick = {},
        destinationController = destination,
        fabController = remember { ShellFabController() },
        topBarController = remember { ShellTopBarController() }
    ) {}
}

private class FixedWindowInfo(override val containerSize: IntSize) : WindowInfo {
    override val isWindowFocused: Boolean = true
}
