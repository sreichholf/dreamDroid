package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class ComposeTvHubStubTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stubHostsChrome() {
        composeRule.setContent {
            ComposeTvHubStub()
        }
        composeRule.onNodeWithTag("compose_tv_hub_chrome").assertExists()
        composeRule.onNodeWithTag("hub_settings_row", useUnmergedTree = true).assertExists()
    }
}
