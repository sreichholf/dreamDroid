package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
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
    fun movingBetweenDrawerHeadersSelectsBouquetGrid() {
        var selected by mutableStateOf(TvComposeHubHost.HEADER_SETTINGS_ID)
        val rows = demoMultiEpgBouquetRows()
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_MULTIEPG_ID, "MultiEPG"),
                    HubNavHeader(rows[0].bouquet.reference, rows[0].bouquet.name)
                ),
                selectedHeaderId = selected,
                onHeaderSelected = { selected = it },
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
                bouquetRows = rows
            )
        }
        // The standard TV drawer updates the page when focus moves from one nav item to
        // another; landing on the first item is not a move.
        composeRule.onNodeWithTag("hub_header_settings", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        assertEquals(TvComposeHubHost.HEADER_SETTINGS_ID, selected)
        val header = composeRule.onNodeWithTag("hub_header_multiepg", useUnmergedTree = true)
        header.assertExists()
        header.requestFocus()
        composeRule.waitForIdle()
        assertEquals(TvComposeHubHost.HEADER_MULTIEPG_ID, selected)
        composeRule.onNodeWithTag("hub_multiepg_bouquet_grid", useUnmergedTree = true)
            .assertExists()
        composeRule.onAllNodesWithTag("hub_settings_row", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_multiepg_row", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_multiepg_open", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_bouquet_multiepg", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun firstServiceCardIsFocusedForTheSelectedBouquet() {
        val rows = demoMultiEpgBouquetRows()
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(rows[0].bouquet.reference, rows[0].bouquet.name),
                    HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timer")
                ),
                selectedHeaderId = rows[0].bouquet.reference,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = rows
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_service_card", useUnmergedTree = true).assertIsFocused()
    }

    /**
     * D-pad Right from the selected bouquet returns to the card focused last, even after the
     * grid scrolled the first card out of the composition.
     */
    @Test
    fun dpadRightFromTheSelectedBouquetReturnsToTheLastFocusedCard() {
        val bouquet = Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites")
        val rows = listOf(
            HubBouquetRow(
                bouquet = bouquet,
                services = (1..60).map { n ->
                    ServiceNowNext(
                        serviceReference = "1:0:1:$n:1:1:1:0:0:0:",
                        serviceName = "Channel $n"
                    )
                }
            )
        )
        showBouquetGrid(rows)
        composeRule.onNodeWithTag("hub_service_grid").performScrollToIndex(45)
        val target = composeRule.onNode(
            hasTestTag("hub_service_card") and hasAnyDescendant(hasText("Channel 46")),
            useUnmergedTree = true
        )
        target.requestFocus()
        composeRule.waitForIdle()
        // The first card left the composition, so Right cannot rely on its requester.
        composeRule.onNode(hasText("Channel 1"), useUnmergedTree = true).assertDoesNotExist()

        val header = composeRule.onNodeWithTag(
            "hub_header_${bouquet.reference}",
            useUnmergedTree = true
        )
        header.requestFocus()
        composeRule.waitForIdle()
        header.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        target.assertIsFocused()
    }

    /** Rows that load after the user moved through the drawer must not pull focus out of it. */
    @Test
    fun movingThroughTheDrawerSuppressesTheInitialCardFocus() {
        val favourites = HubBouquetRow(
            bouquet = Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites"),
            services = emptyList()
        )
        val sports = HubBouquetRow(
            bouquet = Service("1:7:1:0:0:0:0:0:0:0:Sports", "Sports"),
            services = listOf(
                ServiceNowNext(serviceReference = "1:0:1:1:1:1:1:0:0:0:", serviceName = "Sport 1")
            )
        )
        var selected by mutableStateOf(favourites.bouquet.reference)
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(favourites.bouquet.reference, favourites.bouquet.name),
                    HubNavHeader(sports.bouquet.reference, sports.bouquet.name)
                ),
                selectedHeaderId = selected,
                onHeaderSelected = { selected = it },
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = listOf(favourites, sports)
            )
        }
        composeRule.onNodeWithTag(
            "hub_header_${favourites.bouquet.reference}",
            useUnmergedTree = true
        )
            .requestFocus()
        composeRule.waitForIdle()
        val sportsHeader = composeRule.onNodeWithTag(
            "hub_header_${sports.bouquet.reference}",
            useUnmergedTree = true
        )
        sportsHeader.requestFocus()
        composeRule.waitForIdle()

        assertEquals(sports.bouquet.reference, selected)
        sportsHeader.assertIsFocused()
    }

    @Test
    fun serviceCardsShareOneHeight() {
        showBouquetGrid(bouquetWithAndWithoutNow())
        composeRule.waitForIdle()
        val cards = composeRule.onAllNodesWithTag("hub_service_card", useUnmergedTree = true)
        cards.assertCountEquals(2)
        cards[0].assertHeightIsEqualTo(HubServiceCardHeight)
        cards[1].assertHeightIsEqualTo(HubServiceCardHeight)
    }

    @Test
    fun serviceCardShowsCurrentProgramProgressOnlyWhenKnown() {
        showBouquetGrid(bouquetWithAndWithoutNow())
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("hub_service_progress", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    private fun showBouquetGrid(rows: List<HubBouquetRow>) {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(rows[0].bouquet.reference, rows[0].bouquet.name),
                    HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timer")
                ),
                selectedHeaderId = rows[0].bouquet.reference,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = rows
            )
        }
    }

    private fun bouquetWithAndWithoutNow(): List<HubBouquetRow> = listOf(
        HubBouquetRow(
            bouquet = Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites"),
            services = listOf(
                ServiceNowNext(
                    serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                    serviceName = "With now",
                    now = Event(title = "Now", start = "100", duration = "60", currentTime = "100")
                ),
                ServiceNowNext(
                    serviceReference = "1:0:1:2:1:1:1:0:0:0:",
                    serviceName = "Without now"
                )
            )
        )
    )

    @Test
    fun multiEpgBouquetCardClickOpensGraph() {
        var openedRef: String? = null
        var openedName: String? = null
        val rows = demoMultiEpgBouquetRows()
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    HubMultiEpgBouquetGrid(
                        bouquetRows = rows,
                        onOpenMultiEpg = { reference, name ->
                            openedRef = reference
                            openedName = name
                        }
                    )
                }
            }
        }
        composeRule.onAllNodesWithTag("hub_multiepg_row", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_multiepg_open", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("hub_multiepg_bouquet_1", useUnmergedTree = true)
            .assertExists()
        val card = composeRule.onNodeWithTag("hub_multiepg_bouquet_0")
        card.assertIsDisplayed().assertHasClickAction()
        card.requestFocus()
        card.performKeyInput { pressKey(Key.DirectionCenter) }
        if (openedRef == null) {
            card.performClick()
        }
        assertEquals(rows[0].bouquet.reference, openedRef)
        assertEquals(rows[0].bouquet.name, openedName)
    }

    @Test
    fun bouquetServiceGridShowsMultiEpgAction() {
        var openedRef: String? = null
        var openedName: String? = null
        val rows = demoMultiEpgBouquetRows()
        val bouquet = rows[0].bouquet
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(bouquet.reference, bouquet.name)
                ),
                selectedHeaderId = bouquet.reference,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = rows,
                onOpenMultiEpg = { reference, name ->
                    openedRef = reference
                    openedName = name
                }
            )
        }
        val action = composeRule.onNodeWithTag("hub_bouquet_multiepg", useUnmergedTree = true)
        action.assertExists().assertHasClickAction()
        action.requestFocus()
        action.performKeyInput { pressKey(Key.DirectionCenter) }
        if (openedRef == null) {
            action.performClick()
        }
        assertEquals(bouquet.reference, openedRef)
        assertEquals(bouquet.name, openedName)
    }

    @Test
    fun settingsChromeHidesBouquetMultiEpgAction() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_MULTIEPG_ID, "MultiEPG")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
                bouquetRows = demoMultiEpgBouquetRows()
            )
        }
        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_bouquet_multiepg", useUnmergedTree = true)
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

    /** D-pad TV: moving focus from one drawer item to another selects that row. */
    @Test
    fun movingDrawerFocusInvokesCallback() {
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
        // Entering the drawer on its first item is not a nav move, so it must not select.
        composeRule.onNodeWithTag("hub_header_settings", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        assertNull(selected)
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

    @Test
    fun chromeShowsHeadersAndSettingsRow() {
        composeRule.setContent {
            val settingsItems = TvComposeHubHost.defaultSettingsKinds().map { kind ->
                kind to stringResource(TvComposeHubHost.settingsTitleRes(kind))
            }
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_MULTIEPG_ID, "MultiEPG"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services")
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = settingsItems,
                onSettingsClick = {}
            )
        }
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertExists()
        composeRule.onNodeWithTag("hub_header_multiepg", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_header_icon_multiepg", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
    }
}

private fun demoMultiEpgBouquetRows(): List<HubBouquetRow> = listOf(
    HubBouquetRow(
        bouquet = Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites"),
        services = listOf(
            ServiceNowNext(
                serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                serviceName = "Demo"
            )
        )
    ),
    HubBouquetRow(
        bouquet = Service("1:7:1:0:0:0:0:0:0:0:Sports", "Sports"),
        services = emptyList()
    )
)
