package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.services.TvMoviesDestination
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val LIST_TAG = "chrome_scroll_list"
private const val FAB_LABEL = "New timer"

/**
 * Scrolling a hub list down slides the top bar and the bottom chrome (destination bar and
 * now-playing strip) away and collapses the FAB; scrolling up brings them back. Resuming
 * brings back only the bottom chrome.
 */
class ShellChromeScrollTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val owner = TestLifecycleOwner()

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent { ScrollHost(owner) }
        composeRule.waitForIdle()
    }

    @Test
    fun scrollingDownHidesChromeAndScrollingUpBringsItBack() {
        val shownList = listBounds()
        val shownChrome = chromeBounds()
        assertTrue("FAB label shown", fabLabelShown())

        scrollListDown()

        val hiddenList = listBounds()
        assertTrue(
            "top bar gone: list.top ${hiddenList.top} < ${shownList.top}",
            hiddenList.top < shownList.top
        )
        assertTrue(
            "bottom chrome gone: list.bottom ${hiddenList.bottom} >= ${shownChrome.bottom}",
            hiddenList.bottom >= shownChrome.bottom
        )
        composeRule.waitUntil(timeoutMillis = 5_000) { !fabLabelShown() }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(shownList, listBounds())
        assertEquals(shownChrome, chromeBounds())
        composeRule.waitUntil(timeoutMillis = 5_000) { fabLabelShown() }
    }

    @Test
    fun resumeBringsBackBottomChromeOnly() {
        val shownChrome = chromeBounds()
        scrollListDown()
        val hiddenList = listBounds()

        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.waitForIdle()

        assertEquals(shownChrome, chromeBounds())
        assertEquals("top bar stays hidden", hiddenList.top, listBounds().top)
        composeRule.waitUntil(timeoutMillis = 5_000) { fabLabelShown() }
    }

    private fun scrollListDown() {
        composeRule.onNodeWithTag(LIST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
    }

    private fun listBounds(): Rect =
        composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot

    private fun chromeBounds(): Rect =
        composeRule.onNodeWithTag(SHELL_CHROME_TAG).fetchSemanticsNode().boundsInRoot

    private fun fabLabelShown(): Boolean =
        composeRule.onAllNodesWithText(FAB_LABEL).fetchSemanticsNodes().isNotEmpty()
}

@Composable
private fun ScrollHost(owner: LifecycleOwner) {
    val hub = remember {
        TvMoviesHubState().apply {
            selected = TvMoviesDestination.TIMER
            nowPlayingStripEnabled = true
            nowPlayingHeadline = "Das Erste HD · Tagesschau"
        }
    }
    val topBar = remember { ShellTopBarController().apply { title = "Timers" } }
    DreamDroidTheme {
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
            destinationController = remember { ShellDestinationBarController() },
            fabController = remember { ShellFabController() },
            topBarController = topBar,
            usesRail = false
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RegisterShellDestinationBar(ShellDestinationBarContent.TvMovies(hub))
            }
            BindShellFab(
                contentDescription = FAB_LABEL,
                iconRes = R.drawable.ic_action_fab_add,
                onClick = {},
                text = FAB_LABEL
            )
            LazyColumn(Modifier.testTag(LIST_TAG)) {
                items((1..100).toList()) { Text("Timer $it") }
            }
        }
    }
}
