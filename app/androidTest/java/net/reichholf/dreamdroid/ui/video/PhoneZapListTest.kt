package net.reichholf.dreamdroid.ui.video

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneZapListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun currentChannelShowsInFullBelowThePinnedSectionHeader() {
        val services = listOf(ServiceNowNext("1:64:1:0:0:0:0:0:0:0::Doku", "Doku")) +
            (1..30).map { n -> ServiceNowNext("1:0:1:$n:1:1:1:0:0:0:", "Channel $n") }
        composeRule.setContent {
            DreamDroidTheme(forceDark = true) {
                PhoneZapList(
                    services = services,
                    currentRef = "1:0:1:20:1:1:1:0:0:0:",
                    onServiceClick = {}
                )
            }
        }

        val header = composeRule.onNode(hasText("Doku") and isHeading())
            .assertIsDisplayed()
            .getBoundsInRoot()
        val current = composeRule.onNodeWithText("Channel 20")
            .assertIsDisplayed()
            .getBoundsInRoot()
        assertTrue(
            "Channel 20 must start below the header, header=$header current=$current",
            current.top >= header.bottom
        )
    }
}
