package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ComposeTvHubChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun chromeHostsNavigationDrawer() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = listOf(
                    BrowseItem.Kind.Reload to "Reload",
                    BrowseItem.Kind.Preferences to "Settings",
                    BrowseItem.Kind.Profile to "Profile"
                ),
                onSettingsClick = {}
            )
        }
        // Phone AVDs often give the drawer content pane no usable width, so row
        // *cards* may not measure. Assert drawer chrome itself; card activation is
        // covered by settingsRowClickInvokesCallback with a sized parent.
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertExists()
        composeRule.onNodeWithTag("hub_header_settings", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_header_icon_settings", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_header_placeholder", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("compose_tv_hub_rows", useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsRowClickInvokesCallback() {
        var clicked: BrowseItem.Kind? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    HubSettingsRow(
                        settingsItems = listOf(
                            BrowseItem.Kind.Profile to "Profile"
                        ),
                        onSettingsClick = { clicked = it }
                    )
                }
            }
        }
        val node = composeRule.onNodeWithTag("hub_settings_profile")
        node.assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag("hub_settings_icon_profile", useUnmergedTree = true)
            .assertExists()
        // TV Surfaces are D-pad activated; mouse performClick alone is unreliable.
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        if (clicked == null) {
            node.performClick()
        }
        assertEquals(BrowseItem.Kind.Profile, clicked)
    }

    @Test
    fun multiEpgDrawerFocusKeepsCurrentPane() {
        var selected: String? = null
        var opened = 0
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_MULTIEPG_ID, "MultiEPG"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = { selected = it },
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
                onMultiEpgClick = { opened++ }
            )
        }
        val header = composeRule.onNodeWithTag("hub_header_multiepg", useUnmergedTree = true)
        header.assertExists()
        header.requestFocus()
        composeRule.waitForIdle()
        assertEquals(null, selected)
        assertEquals(0, opened)
        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_multiepg_row", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun multiEpgDrawerClickLaunchesWithoutStubCard() {
        var selected: String? = null
        var opened = 0
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_MULTIEPG_ID, "MultiEPG"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = { selected = it },
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
                onMultiEpgClick = { opened++ }
            )
        }
        val header = composeRule.onNodeWithTag("hub_header_multiepg", useUnmergedTree = true)
        header.assertExists()
        header.requestFocus()
        header.performKeyInput { pressKey(Key.DirectionCenter) }
        if (opened == 0) {
            header.performClick()
        }
        assertEquals(1, opened)
        assertEquals(null, selected)
        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_multiepg_open", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun placeholderSelectionShowsPlaceholderRowHost() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                onHeaderSelected = {},
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {}
            )
        }
        composeRule.onNodeWithTag("compose_tv_hub_rows", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_placeholder_row", useUnmergedTree = true).assertExists()
    }

    /** D-pad TV: focusing a header selects that row without requiring a click. */
    @Test
    fun headerFocusInvokesCallback() {
        var selected: String? = null
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = { selected = it },
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {}
            )
        }
        val node = composeRule.onNodeWithTag("hub_header_placeholder", useUnmergedTree = true)
        node.assertExists()
        node.requestFocus()
        composeRule.waitForIdle()
        assertEquals(TvComposeHubHost.HEADER_PLACEHOLDER_ID, selected)
    }

    /** Phase 3.1c-iv-g: drawer header click must drive selection (Leanback parity). */
    @Test
    fun headerClickInvokesCallback() {
        var selected: String? = null
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = { selected = it },
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {}
            )
        }
        val node = composeRule.onNodeWithTag("hub_header_placeholder", useUnmergedTree = true)
        node.assertExists()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        if (selected == null) {
            node.performClick()
        }
        assertEquals(TvComposeHubHost.HEADER_PLACEHOLDER_ID, selected)
    }

    @Test
    fun loadingStateShowsLoadingTag() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                loading = true
            )
        }
        composeRule.onNodeWithTag("hub_loading", useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsHeaderHidesBrowseError() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
                errorText = "box offline"
            )
        }
        composeRule.onAllNodesWithTag("hub_error", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_settings_multiepg", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun settingsHeaderShowsOfflineSessionChip() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
                errorText = "box offline",
                sessionChipLabel = "Offline"
            )
        }
        composeRule.onAllNodesWithTag("hub_error", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("hub_session_chip", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Offline", useUnmergedTree = true).assertExists()
    }

    @Test
    fun errorStateShowsErrorTag() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                errorText = "box offline"
            )
        }
        composeRule.onNodeWithTag("hub_error", useUnmergedTree = true).assertExists()
    }

    @Test
    fun paintedBouquetHidesBrowseError() {
        val bouquet = Service(
            "1:7:1:0:0:0:0:0:0:0:Favourites",
            "Favourites"
        )
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(bouquet.reference, bouquet.name)
                ),
                selectedHeaderId = bouquet.reference,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = listOf(
                    HubBouquetRow(
                        bouquet = bouquet,
                        services = listOf(
                            ServiceNowNext(
                                serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                                serviceName = "Demo"
                            )
                        )
                    )
                ),
                errorText = "box offline"
            )
        }
        composeRule.onAllNodesWithTag("hub_error", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("hub_service_grid", useUnmergedTree = true).assertExists()
    }

    @Test
    fun movieLoadingShowsMovieLoadingTag() {
        val headerId = TvComposeHubHost.movieHeaderId("/hdd/movie")
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(headerId, "/hdd/movie")
                ),
                selectedHeaderId = headerId,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                movieLoading = true,
                moviesByLocation = emptyMap()
            )
        }
        composeRule.onNodeWithTag("hub_movie_loading", useUnmergedTree = true).assertExists()
    }

    @Test
    fun timersHeaderIsInDrawerAfterSettings() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timer"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
                timerContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("hub_timers_host")
                    )
                }
            )
        }
        composeRule.onNodeWithTag("hub_header_settings", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_header_timers", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_timers_host", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun timersSelectionHostsTimerContentNotBrowseRows() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timer"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_TIMERS_ID,
                onHeaderSelected = {},
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
                timerContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("hub_timers_host")
                    )
                }
            )
        }
        composeRule.onNodeWithTag("hub_header_timers", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_timers_host", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_service_row", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_placeholder_row", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_settings_row", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun timersHeaderHidesBrowseError() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timer")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_TIMERS_ID,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                errorText = "box offline",
                timerContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("hub_timers_host")
                    )
                }
            )
        }
        composeRule.onAllNodesWithTag("hub_error", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("hub_timers_host", useUnmergedTree = true).assertExists()
    }
}
