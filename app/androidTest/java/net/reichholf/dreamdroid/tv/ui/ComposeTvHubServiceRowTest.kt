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
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ComposeTvHubServiceRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun serviceRowClickInvokesCallback() {
        var clicked: ServiceNowNext? = null
        var bouquetRef: String? = null
        val service = ServiceNowNext(
            serviceReference = "1:0:1:1:1:1:1:0:0:0:",
            serviceName = "Demo Channel",
            now = Event(title = "Now Show"),
            next = Event(title = "Next Show", startTimeReadable = "20:00"),
        )
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            ) {
                HubServiceRow(
                    bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                    services = listOf(service),
                    onServiceClick = { row, ref ->
                        clicked = row
                        bouquetRef = ref
                    },
                )
            }
        }
        val node = composeRule.onNodeWithTag("hub_service_card")
        node.assertIsDisplayed().assertHasClickAction()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        if (clicked == null) {
            node.performClick()
        }
        assertEquals(service, clicked)
        assertEquals("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET", bouquetRef)
    }

    @Test
    fun bouquetSelectionShowsServiceRowHost() {
        val bouquet = Service(
            reference = "1:7:1:0:0:0:0:0:0:0:Favourites",
            name = "Favourites",
        )
        val service = ServiceNowNext(
            serviceReference = "1:0:1:2:2:2:2:0:0:0:",
            serviceName = "Another",
        )
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(bouquet.reference, bouquet.name),
                ),
                selectedHeaderId = bouquet.reference,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = listOf(
                    HubBouquetRow(bouquet = bouquet, services = listOf(service)),
                ),
            )
        }
        composeRule.onNodeWithTag("compose_tv_hub_rows", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_service_row", useUnmergedTree = true).assertExists()
        assertNotNull(service.serviceReference)
    }
}
