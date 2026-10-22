package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.tv.BrowseItem
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The hub chrome over a real [HubNavigation]: tabs, the drawer per section, the Live TV empty
 * state, and Back. D-pad steps run in keyboard input mode (docs/tv-focus-testing.md).
 */
@OptIn(ExperimentalTestApi::class)
class ComposeTvHubChromeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var inputModeManager: InputModeManager
    private var navigation by mutableStateOf(HubNavigation())
    private var guideShown by mutableStateOf<String?>(null)
    private var backs = 0
    private var reloads = 0
    private var profilesOpened = 0

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
    }

    @Test
    fun liveTvShowsTabsTheBouquetDrawerAndTheTopBar() {
        showHub(locations = listOf(HDD), profileName = "Living Room", sessionChipLabel = "Online")

        for (tab in listOf("live", "guide", "recordings", "timers")) {
            composeRule.onNodeWithTag("hub_tab_$tab")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithTag("hub_tab_live").assertIsSelected()
        composeRule.onNodeWithTag("hub_tab_guide").assertIsNotSelected()
        composeRule.onNodeWithText(context.getString(R.string.tv_hub_live)).assertExists()
        composeRule.onNodeWithTag("hub_drawer").assertExists()
        composeRule.onNodeWithTag("hub_header_$FAVOURITES", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_header_$SPORTS", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_title", useUnmergedTree = true)
            .assert(hasText("Favourites"))
        composeRule.onNodeWithTag("hub_profile_chip")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assert(hasText("Living Room"))
            .assert(hasText("Online"))
        composeRule.onNodeWithTag("hub_action_settings")
            .assertHasClickAction()
            .assertIsNotSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription(context.getString(R.string.settings))
            .assertExists()
    }

    @Test
    fun guideAndRecordingsTabsNeedBouquetsAndLocations() {
        showHub(rows = emptyList(), locations = emptyList())

        composeRule.onNodeWithTag("hub_tab_live").assertExists()
        composeRule.onNodeWithTag("hub_tab_timers").assertExists()
        composeRule.onAllNodesWithTag("hub_tab_guide").assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_tab_recordings").assertCountEquals(0)
    }

    @Test
    fun timersTabShowsFullWidthTimersWithoutDrawer() {
        showHub()
        useKeyboard()

        activate("hub_tab_timers")

        assertEquals(HubSection.Timers, navigation.section)
        composeRule.onNodeWithTag("hub_tab_timers").assertIsSelected()
        composeRule.onNodeWithTag("hub_timers_host", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_drawer").assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_service_grid").assertCountEquals(0)
    }

    @Test
    fun guideOpensOnTheLiveBouquetWithTheBouquetsInTheDrawer() {
        showHub()
        navigation = navigation.select(SPORTS)
        useKeyboard()

        activate("hub_tab_guide")

        assertEquals(HubSection.Guide(SPORTS), navigation.section)
        composeRule.waitUntil { guideShown == SPORTS }
        composeRule.onNodeWithTag("hub_guide").assertExists()
        composeRule.onNodeWithTag("hub_drawer").assertExists()
        activate("hub_header_$FAVOURITES")
        composeRule.waitUntil { guideShown == FAVOURITES }
        assertEquals(HubSection.Guide(FAVOURITES), navigation.section)
    }

    @Test
    fun recordingsListLocationsInTheDrawerAndShowTheSelectedOnesMovies() {
        showHub(
            locations = listOf(HDD, USB),
            moviesByLocation = mapOf(HDD to listOf(movie("On HDD")), USB to listOf(movie("On USB")))
        )
        useKeyboard()

        activate("hub_tab_recordings")

        assertEquals(HubSection.Recordings(HDD), navigation.section)
        composeRule.onNodeWithTag("hub_drawer").assertExists()
        composeRule.onNodeWithTag("hub_header_$HDD", useUnmergedTree = true).assertExists()
        composeRule.onNode(hasText("On HDD"), useUnmergedTree = true).assertExists()
        activate("hub_header_$USB")
        assertEquals(HubSection.Recordings(USB), navigation.section)
        composeRule.onNode(hasText("On USB"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsIconOpensSettingsFullWidthAndShowsItSelected() {
        showHub()
        useKeyboard()

        activate("hub_action_settings")

        assertEquals(HubSection.Settings, navigation.section)
        composeRule.onNodeWithTag("hub_action_settings").assertIsSelected()
        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_drawer").assertCountEquals(0)
        for (tab in listOf("live", "guide", "timers")) {
            composeRule.onNodeWithTag("hub_tab_$tab").assertIsNotSelected()
        }
    }

    @Test
    fun profileChipOpensProfiles() {
        showHub(sessionChipLabel = "Offline")
        useKeyboard()

        activate("hub_profile_chip")

        assertEquals(1, profilesOpened)
    }

    @Test
    fun dpadMovesBetweenTabsSelectButEnteringTheRowDoesNot() {
        showHub()
        useKeyboard()
        activate("hub_action_settings")

        composeRule.onNodeWithTag("hub_tab_live").requestFocus()
        composeRule.waitForIdle()
        assertEquals(HubSection.Settings, navigation.section)

        composeRule.onNodeWithTag("hub_tab_live").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        assertEquals(HubSection.Guide(FAVOURITES), navigation.section)
    }

    @Test
    fun liveTvWithoutBouquetsOffersReloadAndProfiles() {
        showHub(rows = emptyList())
        useKeyboard()

        composeRule.onNodeWithTag("hub_live_empty_message", useUnmergedTree = true)
            .assert(hasText(context.getString(R.string.tv_hub_no_bouquets)))
        composeRule.onAllNodesWithTag("hub_drawer").assertCountEquals(0)
        composeRule.onAllNodesWithTag("hub_error").assertCountEquals(0)
        composeRule.onNodeWithTag("hub_live_empty_reload")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        activate("hub_live_empty_reload")
        assertEquals(1, reloads)
        activate("hub_live_empty_profiles")
        assertEquals(1, profilesOpened)
    }

    @Test
    fun liveTvWithoutBouquetsSaysWhyTheReceiverFailed() {
        showHub(rows = emptyList(), errorText = "box offline")

        composeRule.onNodeWithTag("hub_live_empty_message", useUnmergedTree = true)
            .assert(hasText("box offline"))
    }

    @Test
    fun liveTvWhileLoadingShowsLoadingNotTheEmptyState() {
        showHub(rows = emptyList(), loading = true)

        composeRule.onNodeWithTag("hub_loading", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_live_empty").assertCountEquals(0)
    }

    @Test
    fun backFromAnotherSectionReturnsToLiveTvAndFocusesItsGrid() {
        showHub(locations = listOf(HDD))
        useKeyboard()
        navigation = navigation.select(SPORTS)

        for (tab in listOf("hub_tab_timers", "hub_tab_guide", "hub_tab_recordings")) {
            activate(tab)
            pressBack()
            assertEquals(HubSection.Live(SPORTS), navigation.section)
        }
        activate("hub_action_settings")
        pressBack()
        assertEquals(HubSection.Live(SPORTS), navigation.section)
        composeRule.onNodeWithTag("hub_service_card").assertIsFocused()
        assertEquals(4, backs)
    }

    /** Back on Live TV is not the hub's: it leaves the activity as before. */
    @Test
    fun onlySectionsOtherThanLiveTvTakeBack() {
        showHub()
        val dispatcher = composeRule.activity.onBackPressedDispatcher

        composeRule.runOnIdle { assertFalse(dispatcher.hasEnabledCallbacks()) }
        navigation = navigation.open(HubTab.Timers, demoRows(), emptyList())
        composeRule.runOnIdle { assertTrue(dispatcher.hasEnabledCallbacks()) }
    }

    @Test
    fun settingsRowClickInvokesCallback() {
        var clicked: BrowseItem.Kind? = null
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    HubSettingsRow(
                        settingsItems = listOf(BrowseItem.Kind.Profile to "Profile"),
                        onSettingsClick = { clicked = it }
                    )
                }
            }
        }
        useKeyboard()
        composeRule.onNodeWithTag("hub_settings_icon_profile", useUnmergedTree = true)
            .assertExists()
        activate("hub_settings_profile")
        assertEquals(BrowseItem.Kind.Profile, clicked)
    }

    @Test
    fun firstServiceCardIsFocusedForTheSelectedBouquet() {
        showHub()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_service_card", useUnmergedTree = true).assertIsFocused()
    }

    /**
     * D-pad Right from the selected bouquet returns to the card focused last, even after the
     * grid scrolled the first card out of the composition.
     */
    @Test
    fun dpadRightFromTheSelectedBouquetReturnsToTheLastFocusedCard() {
        val bouquet = Service(FAVOURITES, "Favourites")
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
        showHub(rows = rows)
        useKeyboard()
        composeRule.onNodeWithTag("hub_service_grid").performScrollToIndex(45)
        val target = composeRule.onNode(
            hasTestTag("hub_service_card") and hasAnyDescendant(hasText("Channel 46")),
            useUnmergedTree = true
        )
        target.requestFocus()
        composeRule.waitForIdle()
        // The first card left the composition, so Right cannot rely on its requester.
        composeRule.onNode(hasText("Channel 1"), useUnmergedTree = true).assertDoesNotExist()

        val header = composeRule.onNodeWithTag("hub_header_$FAVOURITES", useUnmergedTree = true)
        header.requestFocus()
        composeRule.waitForIdle()
        header.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        target.assertIsFocused()
    }

    /** Rows that load after the user moved through the drawer must not pull focus out of it. */
    @Test
    fun movingThroughTheDrawerSuppressesTheInitialCardFocus() {
        val favourites = HubBouquetRow(Service(FAVOURITES, "Favourites"), emptyList())
        val sports = HubBouquetRow(
            Service(SPORTS, "Sports"),
            listOf(
                ServiceNowNext(serviceReference = "1:0:1:1:1:1:1:0:0:0:", serviceName = "Sport 1")
            )
        )
        showHub(rows = listOf(favourites, sports))
        useKeyboard()
        composeRule.onNodeWithTag("hub_header_$FAVOURITES", useUnmergedTree = true)
            .requestFocus()
        composeRule.waitForIdle()
        val sportsHeader = composeRule.onNodeWithTag("hub_header_$SPORTS", useUnmergedTree = true)
        sportsHeader.requestFocus()
        composeRule.waitForIdle()

        assertEquals(HubSection.Live(SPORTS), navigation.section)
        sportsHeader.assertIsFocused()
    }

    /** D-pad TV: moving focus from one drawer item to another selects that row. */
    @Test
    fun movingDrawerFocusSelectsButEnteringTheDrawerDoesNot() {
        showHub(rows = listOf(emptyRow(FAVOURITES, "Favourites"), emptyRow(SPORTS, "Sports")))

        composeRule.onNodeWithTag("hub_header_$FAVOURITES", useUnmergedTree = true)
            .requestFocus()
        composeRule.waitForIdle()
        assertEquals(HubSection.Live(FAVOURITES), navigation.section)
        composeRule.onNodeWithTag("hub_header_$SPORTS", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        assertEquals(HubSection.Live(SPORTS), navigation.section)
    }

    @Test
    fun serviceCardsShareOneHeight() {
        showHub(rows = bouquetWithAndWithoutNow())
        composeRule.waitForIdle()
        val cards = composeRule.onAllNodesWithTag("hub_service_card", useUnmergedTree = true)
        cards.assertCountEquals(2)
        cards[0].assertHeightIsEqualTo(HubServiceCardHeight)
        cards[1].assertHeightIsEqualTo(HubServiceCardHeight)
    }

    @Test
    fun serviceCardShowsCurrentProgramProgressOnlyWhenKnown() {
        showHub(rows = bouquetWithAndWithoutNow())
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("hub_service_progress", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun settingsHidesBrowseErrorAndKeepsTheSessionChip() {
        showHub(errorText = "box offline", sessionChipLabel = "Offline")
        navigation = navigation.openSettings()

        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithTag("hub_error", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("hub_session_chip", useUnmergedTree = true)
            .assert(hasText("Offline"))
    }

    @Test
    fun timersHideBrowseError() {
        showHub(errorText = "box offline")
        navigation = navigation.open(HubTab.Timers, demoRows(), emptyList())

        composeRule.onAllNodesWithTag("hub_error", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("hub_timers_host", useUnmergedTree = true).assertExists()
    }

    @Test
    fun emptyRecordingsLocationShowsTheBrowseError() {
        showHub(locations = listOf(HDD), errorText = "box offline")
        navigation = navigation.open(HubTab.Recordings, demoRows(), listOf(HDD))

        composeRule.onNodeWithTag("hub_error", useUnmergedTree = true).assertExists()
    }

    @Test
    fun paintedBouquetHidesBrowseError() {
        showHub(errorText = "box offline")

        composeRule.onAllNodesWithTag("hub_error", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("hub_service_grid", useUnmergedTree = true).assertExists()
    }

    @Test
    fun movieLoadingShowsMovieLoadingTag() {
        showHub(locations = listOf(HDD), movieLoading = true)
        navigation = navigation.open(HubTab.Recordings, demoRows(), listOf(HDD))

        composeRule.onNodeWithTag("hub_movie_loading", useUnmergedTree = true).assertExists()
    }

    private fun showHub(
        rows: List<HubBouquetRow> = demoRows(),
        locations: List<String> = emptyList(),
        moviesByLocation: Map<String, List<Movie>> = emptyMap(),
        loading: Boolean = false,
        movieLoading: Boolean = false,
        errorText: String? = null,
        profileName: String = "",
        sessionChipLabel: String? = "Online"
    ) {
        navigation = HubNavigation().reconcile(rows, locations)
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            HubUnderTest(
                rows,
                locations,
                moviesByLocation,
                loading,
                movieLoading,
                errorText,
                profileName,
                sessionChipLabel
            )
        }
    }

    @Composable
    private fun HubUnderTest(
        rows: List<HubBouquetRow>,
        locations: List<String>,
        moviesByLocation: Map<String, List<Movie>>,
        loading: Boolean,
        movieLoading: Boolean,
        errorText: String?,
        profileName: String,
        sessionChipLabel: String?
    ) {
        ComposeTvHubChrome(
            section = navigation.section,
            onOpenTab = { navigation = navigation.open(it, rows, locations) },
            onOpenSettings = { navigation = navigation.openSettings() },
            onDrawerItemSelected = { navigation = navigation.select(it) },
            onBack = {
                backs++
                navigation.back()?.let { navigation = it }
            },
            settingsItems = TvComposeHubHost.defaultSettingsKinds().map { kind ->
                kind to stringResource(TvComposeHubHost.settingsTitleRes(kind))
            },
            onSettingsClick = {},
            bouquetRows = rows,
            movieLocations = locations,
            moviesByLocation = moviesByLocation,
            loading = loading,
            movieLoading = movieLoading,
            errorText = errorText,
            profileName = profileName,
            sessionChipLabel = sessionChipLabel,
            onOpenProfiles = { profilesOpened++ },
            onReload = { reloads++ },
            timerContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hub_timers_host")
                )
            },
            guideContent = { bouquetRef, _ ->
                SideEffect { guideShown = bouquetRef }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hub_guide")
                )
            }
        )
    }

    private fun useKeyboard() {
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
    }

    /** Focuses [tag] and presses OK, as the remote does. */
    private fun activate(tag: String) {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
        node.assertIsDisplayed()
        node.requestFocus()
        composeRule.waitForIdle()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }
}

private const val FAVOURITES = "1:7:1:0:0:0:0:0:0:0:Favourites"
private const val SPORTS = "1:7:1:0:0:0:0:0:0:0:Sports"
private const val HDD = "/media/hdd/movie/"
private const val USB = "/media/usb/movie/"

private fun demoRows(): List<HubBouquetRow> = listOf(
    HubBouquetRow(
        bouquet = Service(FAVOURITES, "Favourites"),
        services = listOf(
            ServiceNowNext(serviceReference = "1:0:1:1:1:1:1:0:0:0:", serviceName = "Demo")
        )
    ),
    HubBouquetRow(
        bouquet = Service(SPORTS, "Sports"),
        services = listOf(
            ServiceNowNext(serviceReference = "1:0:1:2:1:1:1:0:0:0:", serviceName = "Sport 1")
        )
    )
)

private fun emptyRow(reference: String, name: String) =
    HubBouquetRow(bouquet = Service(reference, name), services = emptyList())

private fun bouquetWithAndWithoutNow(): List<HubBouquetRow> = listOf(
    HubBouquetRow(
        bouquet = Service(FAVOURITES, "Favourites"),
        services = listOf(
            ServiceNowNext(
                serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                serviceName = "With now",
                now = Event(title = "Now", start = "100", duration = "60", currentTime = "100")
            ),
            ServiceNowNext(serviceReference = "1:0:1:2:1:1:1:0:0:0:", serviceName = "Without now")
        )
    )
)

private fun movie(title: String) =
    Movie(reference = "1:0:0:0:0:0:0:0:0:0:$title", title = title, fileName = "$title.ts")
