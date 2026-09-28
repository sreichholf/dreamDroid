package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class ComposeTvHubChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

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
