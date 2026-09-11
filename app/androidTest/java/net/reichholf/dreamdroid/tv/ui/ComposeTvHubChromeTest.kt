package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.tv.BrowseItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ComposeTvHubChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chromeHostsNavigationDrawer() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services"),
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = listOf(
                    BrowseItem.Kind.Reload to "Reload",
                    BrowseItem.Kind.Preferences to "Settings",
                    BrowseItem.Kind.Profile to "Profile",
                ),
                onSettingsClick = {},
            )
        }
        // Phone AVDs often give the drawer content pane no usable width, so row
        // *cards* may not measure. Assert drawer chrome itself; card activation is
        // covered by settingsRowClickInvokesCallback with a sized parent.
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertExists()
        composeRule.onNodeWithTag("hub_header_settings", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_header_placeholder", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("compose_tv_hub_rows", useUnmergedTree = true).assertExists()
    }

    @Test
    fun settingsRowClickInvokesCallback() {
        var clicked: BrowseItem.Kind? = null
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                HubSettingsRow(
                    settingsItems = listOf(
                        BrowseItem.Kind.Profile to "Profile",
                    ),
                    onSettingsClick = { clicked = it },
                )
            }
        }
        val node = composeRule.onNodeWithTag("hub_settings_profile")
        node.assertIsDisplayed().assertHasClickAction()
        // TV Surfaces are D-pad activated; mouse performClick alone is unreliable.
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        if (clicked == null) {
            node.performClick()
        }
        assertEquals(BrowseItem.Kind.Profile, clicked)
    }

    @Test
    fun placeholderSelectionShowsPlaceholderRowHost() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services"),
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                onHeaderSelected = {},
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
            )
        }
        composeRule.onNodeWithTag("compose_tv_hub_rows", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_placeholder_row", useUnmergedTree = true).assertExists()
    }

    /** Phase 3.1c-iv-g: drawer header click must drive selection (Leanback parity). */
    @Test
    fun headerClickInvokesCallback() {
        var selected: String? = null
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services"),
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = { selected = it },
                settingsItems = listOf(BrowseItem.Kind.Reload to "Reload"),
                onSettingsClick = {},
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
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                loading = true,
            )
        }
        composeRule.onNodeWithTag("hub_loading", useUnmergedTree = true).assertExists()
    }

    @Test
    fun errorStateShowsErrorTag() {
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_PLACEHOLDER_ID, "Services"),
                ),
                selectedHeaderId = TvComposeHubHost.HEADER_PLACEHOLDER_ID,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                errorText = "box offline",
            )
        }
        composeRule.onNodeWithTag("hub_error", useUnmergedTree = true).assertExists()
    }

    @Test
    fun movieLoadingShowsMovieLoadingTag() {
        val headerId = TvComposeHubHost.movieHeaderId("/hdd/movie")
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(headerId, "/hdd/movie"),
                ),
                selectedHeaderId = headerId,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                movieLoading = true,
                moviesByLocation = emptyMap(),
            )
        }
        composeRule.onNodeWithTag("hub_movie_loading", useUnmergedTree = true).assertExists()
    }
}
