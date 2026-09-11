package net.reichholf.dreamdroid.tv.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ComposeTvHubStubTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stubLabelVisible() {
        composeRule.setContent {
            ComposeTvHubStub()
        }
        composeRule.onNodeWithTag("compose_tv_hub_stub").assertIsDisplayed()
        composeRule.onNodeWithText("Compose TV hub (stub)").assertIsDisplayed()
    }
}
