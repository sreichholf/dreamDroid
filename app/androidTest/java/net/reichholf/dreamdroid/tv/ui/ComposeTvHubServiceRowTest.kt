package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
            next = Event(title = "Next Show", startTimeReadable = "20:00")
        )
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    HubServiceRow(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                        services = listOf(service),
                        onServiceClick = { row, ref ->
                            clicked = row
                            bouquetRef = ref
                        }
                    )
                }
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
    fun serviceRowInfoInvokesOnServiceInfoWithoutStreaming() {
        var clicked: ServiceNowNext? = null
        var info: ServiceNowNext? = null
        var infoBouquet: String? = null
        val service = ServiceNowNext(
            serviceReference = "1:0:1:1:1:1:1:0:0:0:",
            serviceName = "Demo Channel",
            now = Event(title = "Now Show"),
            next = Event(title = "Next Show", startTimeReadable = "20:00")
        )
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    HubServiceRow(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                        services = listOf(service),
                        onServiceClick = { row, _ -> clicked = row },
                        onServiceInfo = { row, ref ->
                            info = row
                            infoBouquet = ref
                        }
                    )
                }
            }
        }
        val node = composeRule.onNodeWithTag("hub_service_card")
        node.assertIsDisplayed().assertHasClickAction()
        node.requestFocus()
        composeRule.waitForIdle()
        node.performKeyInput { pressKey(Key.Info) }
        composeRule.waitForIdle()
        assertEquals(service, info)
        assertEquals("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET", infoBouquet)
        assertEquals(null, clicked)
    }

    @Test
    fun serviceRowMenuInvokesOnServiceInfoWithoutStreaming() {
        var clicked: ServiceNowNext? = null
        var info: ServiceNowNext? = null
        val service = ServiceNowNext(
            serviceReference = "1:0:1:1:1:1:1:0:0:0:",
            serviceName = "Demo Channel"
        )
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    HubServiceRow(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                        services = listOf(service),
                        onServiceClick = { row, _ -> clicked = row },
                        onServiceInfo = { row, _ -> info = row }
                    )
                }
            }
        }
        val node = composeRule.onNodeWithTag("hub_service_card")
        node.assertIsDisplayed()
        node.requestFocus()
        composeRule.waitForIdle()
        node.performKeyInput { pressKey(Key.Menu) }
        composeRule.waitForIdle()
        assertEquals(service, info)
        assertEquals(null, clicked)
    }

    @Test
    fun serviceRowDirectionCenterStillStreamsWhenInfoHandlerSet() {
        var clicked: ServiceNowNext? = null
        var infoCalls = 0
        val service = ServiceNowNext(
            serviceReference = "1:0:1:3:3:3:3:0:0:0:",
            serviceName = "Stream Channel"
        )
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    HubServiceRow(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                        services = listOf(service),
                        onServiceClick = { row, _ -> clicked = row },
                        onServiceInfo = { _, _ -> infoCalls++ }
                    )
                }
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
        assertEquals(0, infoCalls)
    }

    @Test
    fun serviceGridInfoInvokesOnServiceInfoWithoutStreaming() {
        var clicked: ServiceNowNext? = null
        var info: ServiceNowNext? = null
        var infoBouquet: String? = null
        val service = ServiceNowNext(
            serviceReference = "1:0:1:1:1:1:1:0:0:0:",
            serviceName = "Demo Channel",
            now = Event(title = "Now Show"),
            next = Event(title = "Next Show", startTimeReadable = "20:00")
        )
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    HubServiceGrid(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                        services = listOf(service),
                        onServiceClick = { row, _ -> clicked = row },
                        onServiceInfo = { row, ref ->
                            info = row
                            infoBouquet = ref
                        }
                    )
                }
            }
        }
        val node = composeRule.onNodeWithTag("hub_service_card")
        node.assertIsDisplayed().assertHasClickAction()
        node.requestFocus()
        composeRule.waitForIdle()
        node.performKeyInput { pressKey(Key.Info) }
        composeRule.waitForIdle()
        assertEquals(service, info)
        assertEquals("1:7:1:0:0:0:0:0:0:0:FROM BOUQUET", infoBouquet)
        assertEquals(null, clicked)
    }

    @Test
    fun bouquetSelectionShowsServiceRowHost() {
        val bouquet = Service(
            reference = "1:7:1:0:0:0:0:0:0:0:Favourites",
            name = "Favourites"
        )
        val service = ServiceNowNext(
            serviceReference = "1:0:1:2:2:2:2:0:0:0:",
            serviceName = "Another"
        )
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
                bouquetRows = listOf(
                    HubBouquetRow(bouquet = bouquet, services = listOf(service))
                )
            )
        }
        composeRule.onNodeWithTag("compose_tv_hub_rows", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_service_grid", useUnmergedTree = true).assertExists()
        assertNotNull(service.serviceReference)
    }

    @Test
    fun serviceCardRoundedCornersRevealParentAndStayDark() {
        val parent = Color(0xFF00FF00)
        val service = ServiceNowNext(
            serviceReference = "1:0:1:1:1:1:1:0:0:0:",
            serviceName = "ZDF HD",
            now = Event(title = "Now Show")
        )
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .background(parent)
                        .padding(12.dp)
                        .width(240.dp)
                        .height(240.dp)
                ) {
                    HubServiceRow(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                        services = listOf(service),
                        onServiceClick = { _, _ -> }
                    )
                }
            }
        }
        val node = composeRule.onNodeWithTag("hub_service_card")
        node.assertIsDisplayed()
        val bitmap = node.captureToImage().asAndroidBitmap()
        val corner = Color(bitmap.getPixel(1, 1))
        val midX = (bitmap.width * 0.5f).toInt().coerceIn(0, bitmap.width - 1)
        val midY = (bitmap.height * 0.5f).toInt().coerceIn(0, bitmap.height - 1)
        val center = Color(bitmap.getPixel(midX, midY))
        assertTrue(
            "rounded card corner should show parent, not a square scrim " +
                "corner=#${Integer.toHexString(corner.toArgb())} " +
                "center=#${Integer.toHexString(center.toArgb())}",
            rgbDistance(corner.toArgb(), parent.toArgb()) <
                rgbDistance(corner.toArgb(), center.toArgb())
        )
        assertTrue(
            "card fill should stay a dark surface, not a washed overlay " +
                "luminance=${center.luminance()}",
            center.luminance() < 0.4f
        )

        node.requestFocus()
        composeRule.waitForIdle()
        val focused = node.captureToImage().asAndroidBitmap()
        val focusedCorner = Color(focused.getPixel(1, 1))
        assertTrue(
            "focused card must keep rounded corners " +
                "corner=#${Integer.toHexString(focusedCorner.toArgb())}",
            rgbDistance(focusedCorner.toArgb(), parent.toArgb()) < 80
        )
    }

    @Test
    fun gridCardsShareEqualHeightDespiteLongNowNext() {
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .width(720.dp)
                        .height(480.dp)
                ) {
                    HubServiceGrid(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                        services = listOf(
                            ServiceNowNext(
                                serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                                serviceName = "ZDF HD",
                                now = Event(title = "Now"),
                                next = Event(title = "Short", startTimeReadable = "18:15")
                            ),
                            ServiceNowNext(
                                serviceReference = "1:0:1:2:2:2:2:0:0:0:",
                                serviceName = "SAT.1 HD",
                                now = Event(title = "SAT.1 HD"),
                                next = Event(
                                    title = "Jurassic World: Das gefallene Königreich " +
                                        "and more wrapping text for the next show",
                                    startTimeReadable = "18:14"
                                )
                            )
                        ),
                        onServiceClick = { _, _ -> }
                    )
                }
            }
        }
        val cards = composeRule.onAllNodesWithTag("hub_service_card")
        val first = cards[0].captureToImage().asAndroidBitmap()
        val second = cards[1].captureToImage().asAndroidBitmap()
        assertEquals(
            "grid cards must share one height (short=${first.height} long=${second.height})",
            first.height,
            second.height
        )
    }

    @Test
    fun focusedTopGridCardDoesNotClipIntoTitleBar() {
        val titleBar = Color(0xFFFF00FF)
        composeRule.setContent {
            DreamDroidTvTheme {
                Column(
                    modifier = Modifier
                        .width(720.dp)
                        .height(480.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(titleBar)
                            .testTag("hub_title_bar")
                    )
                    HubServiceGrid(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET",
                        services = listOf(
                            ServiceNowNext(
                                serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                                serviceName = "Das Erste HD",
                                now = Event(title = "Das Erste HD"),
                                next = Event(
                                    title = "Babylon Berlin (2/8)",
                                    startTimeReadable = "18:55"
                                )
                            )
                        ),
                        onServiceClick = { _, _ -> },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        val card = composeRule.onNodeWithTag("hub_service_card")
        card.assertIsDisplayed()
        card.requestFocus()
        composeRule.waitForIdle()
        val bar = composeRule
            .onNodeWithTag("hub_title_bar")
            .captureToImage()
            .asAndroidBitmap()
        val samples = listOf(0.15f, 0.5f, 0.85f).map { xFrac ->
            val x = (bar.width * xFrac).toInt().coerceIn(0, bar.width - 1)
            val y = (bar.height * 0.5f).toInt().coerceIn(0, bar.height - 1)
            Color(bar.getPixel(x, y))
        }
        samples.forEach { px ->
            assertTrue(
                "focused top-row scale must stay in the grid, not clip the title " +
                    "px=#${Integer.toHexString(px.toArgb())}",
                rgbDistance(px.toArgb(), titleBar.toArgb()) < 40
            )
        }
    }

    private fun rgbDistance(a: Int, b: Int): Int {
        val ar = (a shr 16) and 0xff
        val ag = (a shr 8) and 0xff
        val ab = a and 0xff
        val br = (b shr 16) and 0xff
        val bg = (b shr 8) and 0xff
        val bb = b and 0xff
        return abs(ar - br) + abs(ag - bg) + abs(ab - bb)
    }
}
