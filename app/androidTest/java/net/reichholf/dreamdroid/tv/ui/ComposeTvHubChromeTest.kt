package net.reichholf.dreamdroid.tv.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LastLiveStream
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

    /** The density inside the forced TV window, which scales it down to fit the device. */
    private lateinit var density: Density
    private var navigation by mutableStateOf(HubNavigation())
    private var guideShown by mutableStateOf<String?>(null)
    private var backs = 0
    private var reloads = 0
    private var profilesOpened = 0
    private var streamed: Pair<ServiceNowNext, String?>? = null

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
                .assertHeightIsEqualTo(HubTopBarHeight)
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
            .assertHeightIsEqualTo(HubTopBarHeight)
            .assert(hasText("Living Room"))
            .assert(hasText("Online"))
        composeRule.onNodeWithTag("hub_action_settings")
            .assertHasClickAction()
            .assertIsNotSelected()
            .assertHeightIsEqualTo(HubTopBarHeight)
        composeRule.onNodeWithContentDescription(context.getString(R.string.settings))
            .assertExists()
    }

    /** With room to spare, the tabs sit on the top bar's centre, clear of the chip. */
    @Test
    fun tabsAreCentredOnTheTopBarWhenTheyFit() {
        showHub(profileName = "Home", sessionChipLabel = "Online")
        composeRule.waitForIdle()

        val bar = boundsPx("hub_top_bar")
        val tabs = boundsPx("hub_tabs")
        val chip = boundsPx("hub_profile_chip")
        assertEquals(bar.center.x, tabs.center.x, px(2.dp))
        assertTrue("tabs overlap the chip", tabs.right + px(16.dp) <= chip.left + 1f)
    }

    /** A long profile name leaves no room to centre: the tabs start at the bar's padding. */
    @Test
    fun tabsStartAtThePaddingWhenTheChipLeavesNoRoomToCentre() {
        showHub(
            profileName = "The receiver in the living room downstairs next to the kitchen",
            sessionChipLabel = "Online",
            windowSize = DpSize(800.dp, 450.dp)
        )
        composeRule.waitForIdle()

        // The bar's tag sits inside its padding: its bounds are the padded content box.
        val hub = boundsPx("compose_tv_hub_chrome")
        val bar = boundsPx("hub_top_bar")
        val tabs = boundsPx("hub_tabs")
        val chip = boundsPx("hub_profile_chip")
        val settings = boundsPx("hub_action_settings")
        assertEquals(hub.left + px(32.dp), bar.left, px(1.dp))
        assertEquals(hub.right - px(32.dp), bar.right, px(1.dp))
        assertEquals(bar.left, tabs.left, px(2.dp))
        assertEquals(bar.right, settings.right, px(2.dp))
        assertTrue("tabs overlap the chip", tabs.right + px(16.dp) <= chip.left + 1f)
        assertTrue(
            "the profile name keeps its ellipsis cap",
            boundsPx("hub_profile_name").width <= px(220.dp) + 1f
        )
    }

    /**
     * The "Live TV" label, the chip's connection word and the Settings glyph are centred on
     * their 48dp controls, measured on the drawn pixels.
     */
    @Test
    fun topBarLabelsAndSettingsGlyphSitOnTheirControlsCentre() {
        showHub(profileName = "Living Room", sessionChipLabel = "Online")
        composeRule.waitForIdle()
        val tolerance = px(1.5.dp)

        for (tag in listOf("hub_tab_live", "hub_profile_chip", "hub_action_settings")) {
            assertEquals("$tag height", px(HubTopBarHeight), boundsPx(tag).height, 1f)
        }

        val tab = capture("hub_tab_live")
        val tabInk = inkRows(
            tab,
            background = tab.getPixel(px(6.dp).toInt(), tab.height / 2),
            from = px(12.dp).toInt(),
            to = tab.width - px(12.dp).toInt()
        )
        assertEquals("Live TV label", tab.height / 2f, tabInk.centre(), tolerance)

        val chipBounds = boundsPx("hub_profile_chip")
        val word = boundsPx("hub_session_chip")
        val chip = capture("hub_profile_chip")
        val wordInk = inkRows(
            chip,
            background = chip.getPixel(px(6.dp).toInt(), chip.height / 2),
            from = (word.left - chipBounds.left).toInt(),
            to = (word.right - chipBounds.left).toInt()
        )
        assertEquals("chip text", chip.height / 2f, wordInk.centre(), tolerance)

        val settings = capture("hub_action_settings")
        val glyph = inkRows(
            settings,
            background = settings.getPixel(px(6.dp).toInt(), settings.height / 2),
            from = px(10.dp).toInt(),
            to = settings.width - px(10.dp).toInt()
        )
        assertEquals("Settings glyph", settings.height / 2f, glyph.centre(), tolerance)
        // A 20dp icon like the labels, not a badge filling the button.
        assertTrue("Settings glyph too big", glyph.last - glyph.first + 1 <= px(20.dp) + 1f)
    }

    /**
     * While the tab row has focus the selected tab sits on a light pill. Its label must stay
     * readable there, both when focus entered the row on another tab (no selection change) and
     * when the selected tab holds focus itself.
     */
    @Test
    fun selectedTabLabelStaysReadableOnTheFocusedRowsPill() {
        showHub()
        useKeyboard()

        composeRule.onNodeWithTag("hub_tab_guide", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_tab_live").assertIsSelected()
        assertLabelContrast("hub_tab_live", "Live TV, focus on Guide")

        composeRule.onNodeWithTag("hub_tab_live", useUnmergedTree = true).requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_tab_live").assertIsSelected()
        assertLabelContrast("hub_tab_live", "Live TV, focused")
    }

    /**
     * With focus in the content, the unselected tabs sit on the bare bar. TV's default 40%
     * alpha falls below text contrast there; they keep full onSurfaceVariant.
     */
    @Test
    fun unselectedTabLabelsStayReadableWithoutRowFocus() {
        showHub()
        useKeyboard()
        composeRule.onAllNodesWithTag("hub_service_card", useUnmergedTree = true)
            .onFirst()
            .requestFocus()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hub_tab_guide").assertIsNotSelected()
        assertLabelContrast("hub_tab_guide", "Guide, focus in the content")
        assertLabelContrast("hub_tab_live", "Live TV, focus in the content")
    }

    /**
     * The profile chip looks like a tab: no fill at rest, and on focus the light pill with the
     * profile name and the status word both readable on it.
     */
    @Test
    fun profileChipHasNoFillAtRestAndAReadablePillOnFocus() {
        showHub(profileName = "Living Room", sessionChipLabel = "Online")
        useKeyboard()
        composeRule.waitForIdle()

        val bar = capture("hub_top_bar")
        val chipBounds = boundsPx("hub_profile_chip")
        val barBounds = boundsPx("hub_top_bar")
        val rest = capture("hub_profile_chip")
        val barBeside = bar.getPixel(
            (chipBounds.left - barBounds.left - px(4.dp)).toInt(),
            (chipBounds.center.y - barBounds.top).toInt()
        )
        val chipPadding = rest.getPixel(px(4.dp).toInt(), rest.height / 2)
        assertTrue(
            "chip has a fill at rest: #${Integer.toHexString(chipPadding)} vs bar " +
                "#${Integer.toHexString(barBeside)}",
            rgbDistance(chipPadding, barBeside) <= INK_THRESHOLD
        )

        composeRule.onNodeWithTag("hub_profile_chip").requestFocus()
        composeRule.waitForIdle()
        val chip = capture("hub_profile_chip")
        val pill = chip.getPixel(px(6.dp).toInt(), chip.height / 2)
        for (tag in listOf("hub_profile_name", "hub_session_chip")) {
            val word = boundsPx(tag)
            val band = (chip.height / 2 - px(6.dp).toInt())..(chip.height / 2 + px(6.dp).toInt())
            val ink = (
                (word.left - chipBounds.left).toInt() until
                    (word.right - chipBounds.left).toInt()
                )
                .flatMap { x -> band.map { y -> chip.getPixel(x, y) } }
                .maxBy { rgbDistance(it, pill) }
            val ratio = contrast(pill, ink)
            assertTrue("$tag on the focused chip: contrast $ratio", ratio >= 4.5)
        }
    }

    /** The collapsed drawer's selected icon lines up with the section title beside it. */
    @Test
    fun drawerIconLinesUpWithTheSectionTitle() {
        showHub()
        composeRule.waitForIdle()

        val icon = boundsPx("hub_header_icon_$FAVOURITES")
        val titleBounds = boundsPx("hub_title")
        val title = capture("hub_title")
        val titleInk = inkRows(title, background = title.getPixel(0, 0))
        assertEquals(icon.center.y, titleBounds.top + titleInk.centre(), px(2.dp))
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

    /**
     * The Guide has no hub drawer: the MultiEPG's own bouquet chip is its only bouquet
     * selector, it uses the full width, and a bouquet picked there is Live TV's too.
     */
    @Test
    fun guideOpensOnTheLiveBouquetFullWidthWithoutTheHubDrawer() {
        showHub()
        navigation = navigation.select(SPORTS)
        useKeyboard()

        activate("hub_tab_guide")

        assertEquals(HubSection.Guide(SPORTS), navigation.section)
        composeRule.waitUntil { guideShown == SPORTS }
        composeRule.onAllNodesWithTag("hub_drawer").assertCountEquals(0)
        // The pane's tag sits inside its 32dp padding: its bounds are the content box, which
        // starts at the hub's padding when no drawer takes its start.
        val hub = boundsPx("compose_tv_hub_chrome")
        val pane = boundsPx("compose_tv_hub_rows")
        val guide = boundsPx("hub_guide")
        assertEquals(hub.left + px(32.dp), pane.left, px(1.dp))
        assertEquals(hub.right - px(32.dp), pane.right, px(1.dp))
        assertEquals(pane.left, guide.left, px(1.dp))
        assertEquals(pane.right, guide.right, px(1.dp))

        // What the MultiEPG's chip does (TvMultiEpgHost's onBouquetChange).
        composeRule.runOnIdle { guidePicksBouquet(FAVOURITES) }
        composeRule.waitUntil { guideShown == FAVOURITES }
        assertEquals(HubSection.Guide(FAVOURITES), navigation.section)
        activate("hub_tab_live")
        assertEquals(HubSection.Live(FAVOURITES), navigation.section)
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

    /** With a saved channel, Back lands where Live TV starts: the Continue watching card. */
    @Test
    fun backFromAnotherSectionFocusesContinueWatchingWhenItShows() {
        showHub(lastStream = LAST_CHANNEL)
        useKeyboard()

        activate("hub_tab_timers")
        pressBack()

        assertEquals(HubSection.Live(FAVOURITES), navigation.section)
        composeRule.onNodeWithTag("hub_continue_watching").assertIsFocused()
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

    @Test
    fun continueWatchingShowsTheLastChannelAndPlaysItLikeItsCard() {
        showHub(lastStream = LAST_CHANNEL)
        useKeyboard()

        composeRule.onNodeWithTag("hub_continue_watching")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assertHeightIsEqualTo(HubServiceCardHeight)
            .assert(hasText(context.getString(R.string.tv_hub_continue_watching)))
            .assert(hasText("ZDF HD"))
        activate("hub_continue_watching")

        assertEquals(
            ServiceNowNext(serviceReference = LAST_CHANNEL.reference, serviceName = "ZDF HD") to
                SPORTS,
            streamed
        )
    }

    @Test
    fun noContinueWatchingWithoutASavedChannel() {
        showHub()

        composeRule.onAllNodesWithTag("hub_continue_watching").assertCountEquals(0)
        composeRule.onNodeWithTag("hub_service_grid", useUnmergedTree = true).assertExists()
    }

    /** Continue watching is Live TV's first card: initial focus, then Down into the grid. */
    @Test
    fun continueWatchingTakesTheInitialFocusAndDownEntersTheGrid() {
        showHub(lastStream = LAST_CHANNEL)
        useKeyboard()

        val card = composeRule.onNodeWithTag("hub_continue_watching")
        card.assertIsFocused()
        card.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hub_service_card", useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun upFromTheFirstServiceReturnsToContinueWatching() {
        showHub(lastStream = LAST_CHANNEL)
        useKeyboard()
        val service = composeRule.onNodeWithTag("hub_service_card", useUnmergedTree = true)
        service.requestFocus()
        composeRule.waitForIdle()

        service.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hub_continue_watching").assertIsFocused()
    }

    /** Continue watching is the grid's first item: it scrolls away with the services. */
    @Test
    fun continueWatchingScrollsAwayWithTheGrid() {
        val rows = listOf(
            HubBouquetRow(
                bouquet = Service(FAVOURITES, "Favourites"),
                services = (1..60).map { n ->
                    ServiceNowNext(
                        serviceReference = "1:0:1:$n:1:1:1:0:0:0:",
                        serviceName = "Channel $n"
                    )
                }
            )
        )
        showHub(rows = rows, lastStream = LAST_CHANNEL)
        composeRule.onNode(
            hasTestTag("hub_service_grid") and hasAnyDescendant(hasTestTag("hub_continue_watching"))
        ).assertExists()

        composeRule.onNodeWithTag("hub_service_grid").performScrollToIndex(50)
        composeRule.waitForIdle()

        composeRule.onNode(hasText("Channel 50"), useUnmergedTree = true).assertIsDisplayed()
        // The card holds the initial focus, and a lazy grid keeps its focused item composed
        // (pinned) after it scrolls out: it must sit above the grid's viewport, not on it.
        val grid = boundsPx("hub_service_grid")
        val cards = composeRule.onAllNodesWithTag("hub_continue_watching")
            .fetchSemanticsNodes()
        for (card in cards) {
            assertTrue(
                "Continue watching still in the grid: ${card.boundsInRoot} grid=$grid",
                card.boundsInRoot.bottom <= grid.top
            )
        }
        if (cards.isNotEmpty()) {
            composeRule.onNodeWithTag("hub_continue_watching").assertIsNotDisplayed()
        }
    }

    /** A long channel name takes one line with an ellipsis; the card keeps its height. */
    @Test
    fun continueWatchingKeepsOneLineForALongName() {
        val long = LAST_CHANNEL.copy(
            serviceName = "A channel name far too long to fit on the continue watching card"
        )
        showHub(lastStream = long)

        // A service card's size, not stretched across the grid's line.
        composeRule.onNodeWithTag("hub_continue_watching")
            .assertHeightIsEqualTo(HubServiceCardHeight)
            .assertWidthIsEqualTo(HubServiceRowCardWidth)
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("hub_continue_watching_name", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertTrue("the long name is not ellipsized", layouts.single().isLineEllipsized(0))
    }

    @Test
    fun dpadRightFromTheSelectedBouquetLandsOnContinueWatching() {
        showHub(lastStream = LAST_CHANNEL)
        useKeyboard()
        val header = composeRule.onNodeWithTag("hub_header_$FAVOURITES", useUnmergedTree = true)
        header.requestFocus()
        composeRule.waitForIdle()

        header.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hub_continue_watching").assertIsFocused()
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
        sessionChipLabel: String? = "Online",
        lastStream: LastLiveStream? = null,
        windowSize: DpSize = TvWindow
    ) {
        navigation = HubNavigation().reconcile(rows, locations)
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            // The hub is a TV layout: lay it out at a TV window, not the phone's portrait width.
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowSize)) {
                density = LocalDensity.current
                HubUnderTest(
                    rows,
                    locations,
                    moviesByLocation,
                    loading,
                    movieLoading,
                    errorText,
                    profileName,
                    sessionChipLabel,
                    lastStream
                )
            }
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
        sessionChipLabel: String?,
        lastStream: LastLiveStream?
    ) {
        ComposeTvHubChrome(
            section = navigation.section,
            onOpenTab = { navigation = navigation.open(it, rows, locations) },
            onOpenSettings = { navigation = navigation.openSettings() },
            onDrawerItemSelected = ::guidePicksBouquet,
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
            onServiceClick = { service, bouquetRef -> streamed = service to bouquetRef },
            lastStream = lastStream,
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

    /** The hub's selection callback, which the Guide's bouquet chip calls too. */
    private fun guidePicksBouquet(ref: String) {
        navigation = navigation.select(ref)
    }

    /** [dp] of the hub, whose density the forced TV window scales, in device pixels. */
    private fun px(dp: Dp): Float = with(density) { dp.toPx() }

    private fun boundsPx(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun capture(tag: String): Bitmap =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()

    /**
     * The rows of [image] that hold ink: a pixel between columns [from] and [to] that differs
     * clearly from [background], the control's own fill.
     */
    private fun inkRows(
        image: Bitmap,
        background: Int,
        from: Int = 0,
        to: Int = image.width
    ): IntRange {
        val rows = (0 until image.height).filter { y ->
            (from.coerceAtLeast(0) until to.coerceAtMost(image.width)).any { x ->
                rgbDistance(image.getPixel(x, y), background) > INK_THRESHOLD
            }
        }
        assertTrue("no ink found", rows.isNotEmpty())
        return rows.first()..rows.last()
    }

    /**
     * The [tag] tab label's strongest ink reaches WCAG's 4.5:1 for text against what lies
     * behind it, sampled in the tab's side padding: the pill, or the bare bar.
     */
    private fun assertLabelContrast(tag: String, state: String) {
        val tab = capture(tag)
        val pill = tab.getPixel(px(6.dp).toInt(), tab.height / 2)
        // The label's middle band, clear of the pill's rounded ends and the bar behind them.
        val band = (tab.height / 2 - px(8.dp).toInt())..(tab.height / 2 + px(8.dp).toInt())
        val label = (px(16.dp).toInt() until tab.width - px(16.dp).toInt()).flatMap { x ->
            band.map { y -> tab.getPixel(x, y) }
        }.maxBy { rgbDistance(it, pill) }
        val ratio = contrast(pill, label)
        assertTrue(
            "$state: label contrast $ratio, pill=#${Integer.toHexString(pill)} " +
                "label=#${Integer.toHexString(label)}",
            ratio >= 4.5
        )
    }

    private fun contrast(a: Int, b: Int): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(AndroidColor.red(color)) +
            0.7152 * channel(AndroidColor.green(color)) +
            0.0722 * channel(AndroidColor.blue(color))
    }

    private fun IntRange.centre(): Float = (first + last + 1) / 2f

    private fun rgbDistance(a: Int, b: Int): Int = abs(AndroidColor.red(a) - AndroidColor.red(b)) +
        abs(AndroidColor.green(a) - AndroidColor.green(b)) +
        abs(AndroidColor.blue(a) - AndroidColor.blue(b))

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

/** Sum of the RGB differences above which a pixel is a glyph, not its control's fill. */
private const val INK_THRESHOLD = 120

/** A 1080p TV at its usual 2.0 density. */
private val TvWindow = DpSize(960.dp, 540.dp)

private const val FAVOURITES = "1:7:1:0:0:0:0:0:0:0:Favourites"
private const val SPORTS = "1:7:1:0:0:0:0:0:0:0:Sports"
private const val HDD = "/media/hdd/movie/"
private const val USB = "/media/usb/movie/"

/** Saved from Sports while Favourites is on screen: the card shows for any bouquet. */
private val LAST_CHANNEL = LastLiveStream("1:0:19:2B66:3F3:1:C00000:0:0:0:", "ZDF HD", SPORTS)

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
