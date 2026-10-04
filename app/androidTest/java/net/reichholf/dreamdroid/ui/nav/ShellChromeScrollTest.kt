package net.reichholf.dreamdroid.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.compose.ListEmptyState
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.services.TvMoviesDestination
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val CONTENT_TAG = "chrome_scroll_content"
private const val LIST_TAG = "chrome_scroll_list"
private const val FAB_LABEL = "New timer"
private const val LIST_SCREEN_TAG = "chrome_scroll_list_screen"
private const val DETAIL_SCREEN_TAG = "chrome_scroll_detail_screen"

/** How the destination in [ScrollHost] uses the shell top bar. */
private enum class TopBarUse { SHELL, KEEP_IN_VIEW, REPLACED }

/**
 * Scrolling a hub list down slides the top bar and the bottom chrome (destination bar and
 * now-playing strip) away and collapses the FAB; scrolling up brings them back. Resuming
 * brings back only the bottom chrome.
 */
class ShellChromeScrollTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val owner = TestLifecycleOwner()
    private val listState = LazyListState()
    private var showEmpty by mutableStateOf(false)

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
    }

    @Test
    fun scrollingDownHidesChromeAndScrollingUpBringsItBack() {
        show()
        val shown = contentBounds()
        val shownChrome = chromeBounds()
        assertTrue("content clear of chrome", shown.bottom <= shownChrome.top)
        assertTrue("FAB label shown", fabLabelShown())

        scrollListDown()

        assertChromeHidden(shown, shownChrome)
        composeRule.waitUntil(timeoutMillis = 5_000) { !fabLabelShown() }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(shown, contentBounds())
        assertEquals(shownChrome, chromeBounds())
        composeRule.waitUntil(timeoutMillis = 5_000) { fabLabelShown() }
    }

    @Test
    fun resumeBringsBackBottomChromeOnly() {
        show()
        val shown = contentBounds()
        val shownChrome = chromeBounds()
        scrollListDown()
        assertChromeHidden(shown, shownChrome)
        val hiddenTop = contentBounds().top

        composeRule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.waitForIdle()

        assertEquals(shownChrome, chromeBounds())
        assertEquals("top bar stays hidden", hiddenTop, contentBounds().top)
        assertFalse("FAB label follows the top bar", fabLabelShown())
    }

    @Test
    fun tabletNowPlayingStripHidesToo() {
        show(usesRail = true)
        val shown = contentBounds()
        val shownStrip = chromeBounds()
        assertTrue("content clear of strip", shown.bottom <= shownStrip.top)

        scrollListDown()

        assertChromeHidden(shown, shownStrip)
    }

    @Test
    fun keptTopBarStaysWhileBottomChromeHides() {
        show(topBar = TopBarUse.KEEP_IN_VIEW)
        val shown = contentBounds()
        val shownChrome = chromeBounds()

        scrollListDown()

        val scrolled = contentBounds()
        assertEquals("top bar kept", shown.top, scrolled.top)
        assertTrue("bottom chrome gone", scrolled.bottom >= shownChrome.bottom)
    }

    @Test
    fun replacedTopBarDoesNotSwallowScrolling() {
        show(topBar = TopBarUse.REPLACED)
        // Shorter than the shell top bar: an unseen bar would take all of it.
        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipe(start = center, end = center.copy(y = center.y - 48.dp.toPx()), 500)
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue(
                "list scrolled",
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            )
        }
    }

    @Test
    fun emptyStateBringsBackTheTopBar() {
        show()
        val shown = contentBounds()
        scrollListDown()
        assertTrue("top bar hidden", contentBounds().top < shown.top)

        composeRule.runOnIdle { showEmpty = true }
        composeRule.waitForIdle()

        assertEquals(shown.top, contentBounds().top)
    }

    @Test
    fun backFindsTheBarsWhereTheListLeftThem() {
        lateinit var nav: NavHostController
        composeRule.setContent {
            nav = rememberNavController()
            NavScrollHost(nav)
        }
        composeRule.waitForIdle()
        val shownTop = boundsOf(LIST_SCREEN_TAG).top
        scrollListDown()
        val hiddenTop = boundsOf(LIST_SCREEN_TAG).top
        assertTrue("top bar hidden", hiddenTop < shownTop)

        composeRule.runOnIdle { nav.navigate("detail") }
        composeRule.waitForIdle()
        assertEquals("new screen shows the top bar", shownTop, boundsOf(DETAIL_SCREEN_TAG).top)

        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.waitForIdle()
        assertEquals("Back keeps the list's bars", hiddenTop, boundsOf(LIST_SCREEN_TAG).top)
    }

    private fun show(usesRail: Boolean = false, topBar: TopBarUse = TopBarUse.SHELL) {
        composeRule.setContent {
            ScrollHost(
                owner = owner,
                listState = listState,
                showEmpty = showEmpty,
                usesRail = usesRail,
                topBar = topBar
            )
        }
        composeRule.waitForIdle()
    }

    private fun scrollListDown() {
        composeRule.onNodeWithTag(LIST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()
    }

    private fun assertChromeHidden(shown: Rect, shownChrome: Rect) {
        val hidden = contentBounds()
        assertTrue("top bar gone: top ${hidden.top} < ${shown.top}", hidden.top < shown.top)
        assertTrue(
            "bottom chrome gone: bottom ${hidden.bottom} >= ${shownChrome.bottom}",
            hidden.bottom >= shownChrome.bottom
        )
    }

    private fun contentBounds(): Rect = boundsOf(CONTENT_TAG)

    private fun boundsOf(tag: String): Rect =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun chromeBounds(): Rect =
        composeRule.onNodeWithTag(SHELL_CHROME_TAG).fetchSemanticsNode().boundsInRoot

    private fun fabLabelShown(): Boolean =
        composeRule.onAllNodesWithText(FAB_LABEL).fetchSemanticsNodes().isNotEmpty()
}

@Composable
private fun ScrollHost(
    owner: LifecycleOwner,
    listState: LazyListState,
    showEmpty: Boolean,
    usesRail: Boolean,
    topBar: TopBarUse
) {
    val hub = remember {
        TvMoviesHubState().apply {
            selected = TvMoviesDestination.TIMER
            nowPlayingStripEnabled = true
            nowPlayingHeadline = "Das Erste HD · Tagesschau"
        }
    }
    val topBarController = remember { ShellTopBarController().apply { title = "Timers" } }
    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
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
                topBarController = topBarController,
                usesRail = usesRail
            ) {
                RegisterShellDestinationBar(ShellDestinationBarContent.TvMovies(hub))
                when (topBar) {
                    TopBarUse.SHELL -> Unit

                    TopBarUse.KEEP_IN_VIEW -> BindShellTopBarActions(
                        listOf(ShellTopBarAction(id = R.id.menu_save, label = "Save") {}),
                        keepInView = true
                    )

                    TopBarUse.REPLACED -> ReplaceShellTopBar()
                }
                BindShellFab(
                    contentDescription = FAB_LABEL,
                    iconRes = R.drawable.ic_action_fab_add,
                    onClick = {},
                    text = FAB_LABEL
                )
                Box(Modifier.fillMaxSize().testTag(CONTENT_TAG)) {
                    if (showEmpty) {
                        ListEmptyState(loading = false, message = "No timers")
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.testTag(LIST_TAG)) {
                            items((1..100).toList()) { Text("Timer $it") }
                        }
                    }
                }
            }
        }
    }
}

/** The shell around a NavHost with a long list and a detail screen. */
@Composable
private fun NavScrollHost(nav: NavHostController) {
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
            topBarController = remember { ShellTopBarController().apply { title = "Timers" } },
            usesRail = false
        ) {
            val entry by nav.currentBackStackEntryAsState()
            KeepShellChromePerScreen(entry)
            NavHost(navController = nav, startDestination = "list") {
                composable("list") {
                    Box(Modifier.fillMaxSize().testTag(LIST_SCREEN_TAG)) {
                        LazyColumn(Modifier.testTag(LIST_TAG)) {
                            items((1..100).toList()) { Text("Timer $it") }
                        }
                    }
                }
                composable("detail") {
                    Box(Modifier.fillMaxSize().testTag(DETAIL_SCREEN_TAG)) {
                        Text("Timer 1")
                    }
                }
            }
        }
    }
}
