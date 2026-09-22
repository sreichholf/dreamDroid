package net.reichholf.dreamdroid.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.drawer.DrawerListState
import net.reichholf.dreamdroid.ui.services.TvMoviesDestination
import net.reichholf.dreamdroid.ui.services.TvMoviesHubState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Timer create FAB must sit above TV & Movies chrome (now-playing strip + destination bar).
 */
class DodgeShellChromeFabTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
    }

    @Test
    fun newTimerFabSitsAboveNowPlayingStrip() {
        showFabOverChrome(stripEnabled = true)
        assertFabAboveChrome()
    }

    @Test
    fun newTimerFabSitsAboveDestinationBarWhenStripOff() {
        showFabOverChrome(stripEnabled = false)
        assertFabAboveChrome()
    }

    private fun showFabOverChrome(stripEnabled: Boolean) {
        composeRule.setContent {
            ChromeHost(stripEnabled = stripEnabled)
        }
        composeRule.waitForIdle()
    }

    private fun assertFabAboveChrome() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val chrome = composeRule.onNodeWithTag(SHELL_CHROME_TAG).fetchSemanticsNode()
            val fab = composeRule.onNodeWithTag(SHELL_FAB_TAG).fetchSemanticsNode()
            chrome.boundsInRoot.height > 0f &&
                fab.boundsInRoot.bottom <= chrome.boundsInRoot.top
        }
        val chrome = composeRule.onNodeWithTag(SHELL_CHROME_TAG).fetchSemanticsNode().boundsInRoot
        val fab = composeRule.onNodeWithTag(SHELL_FAB_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "fab.bottom=${fab.bottom} chrome.top=${chrome.top}",
            fab.bottom <= chrome.top
        )
    }
}

@Composable
private fun ChromeHost(stripEnabled: Boolean) {
    val destination = remember {
        ShellDestinationBarController().apply {
            content = ShellDestinationBarContent.TvMovies(
                TvMoviesHubState().apply {
                    selected = TvMoviesDestination.TIMER
                    nowPlayingStripEnabled = stripEnabled
                    nowPlayingHeadline = "Das Erste HD · Tagesschau"
                }
            )
        }
    }
    val fab = remember { ShellFabController() }
    DreamDroidTheme {
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
            fabController = fab,
            onToolbarReady = {},
            usesRail = false
        ) {
            BindShellFab(
                contentDescription = "New timer",
                iconRes = R.drawable.ic_action_fab_add,
                onClick = {},
                text = "New timer"
            )
        }
    }
}
