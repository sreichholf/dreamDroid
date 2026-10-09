package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.testutil.WithWindowSize
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
 * configuration is left alone. From medium width on, content is also inset by the Material 3
 * window margin.
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
    fun mediumWindowInsetsContentByMaterialMargin() {
        showShell(width = 700.dp, height = 900.dp)
        val margin = contentStartMargin()
        assertTrue("expected a 24dp inset, was $margin dp", abs(margin - 24f) < 1f)
    }

    @Test
    fun compactWindowDrawsContentEdgeToEdge() {
        showShell(width = 400.dp, height = 800.dp)
        val margin = contentStartMargin()
        assertTrue("expected no inset, was $margin dp", abs(margin) < 1f)
    }

    @Test
    fun compactWindowPutsIconsAboveLabels() {
        showShell(width = 400.dp, height = 800.dp)
        composeRule.onNodeWithTag(SHELL_CHROME_TAG).assertIsDisplayed()
        val offset = labelOffsetFromItemCenter()
        assertTrue("label below the icon, off by $offset", offset > 4f)
    }

    /** Distance in dp from the shell chrome's start edge to the content's start edge. */
    private fun contentStartMargin(): Float {
        val chrome = composeRule.onNodeWithTag(SHELL_CHROME_TAG).fetchSemanticsNode().boundsInRoot
        val content = composeRule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().boundsInRoot
        return with(composeRule.density) { (content.left - chrome.left).toDp().value }
    }

    /** How far, in dp, the selected item's label sits below the middle of the item. */
    private fun labelOffsetFromItemCenter(): Float {
        val item = composeRule.onNodeWithText("Timer").fetchSemanticsNode().boundsInRoot
        val label = composeRule.onNodeWithText("Timer", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        return with(composeRule.density) { (label.center.y - item.center.y).toDp().value }
    }

    private fun showShell(width: Dp, height: Dp) {
        composeRule.setContent {
            DreamDroidTheme {
                WithWindowSize(width, height) { WindowSizeHost() }
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
    ) {
        Box(Modifier.fillMaxSize().testTag(CONTENT_TAG))
    }
}

private const val CONTENT_TAG = "window_size_content"
