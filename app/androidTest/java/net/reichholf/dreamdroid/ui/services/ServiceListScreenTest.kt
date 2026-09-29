package net.reichholf.dreamdroid.ui.services

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_SURFACE_TAG
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServiceListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun markersAreStickySectionHeaders() {
        val channels = (1..30).map { n ->
            ServiceListItem(
                index = n,
                reference = "1:0:1:$n:1:1:1:0:0:0:",
                name = "Channel $n",
                kind = ServiceRowKind.CHANNEL
            )
        }
        val items = listOf(
            ServiceListItem(0, "1:64:1:0:0:0:0:0:0:0::Doku", "Doku", ServiceRowKind.MARKER)
        ) + channels + ServiceListItem(
            31,
            "1:64:2:0:0:0:0:0:0:0::Sport",
            "Sport",
            ServiceRowKind.MARKER
        )
        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(items = items, onItemClick = {}, onItemLongClick = {})
            }
        }
        composeRule.onNode(hasText("Doku") and isHeading()).assertIsDisplayed()

        // Channel 20 is list index 20: the marker is one entry, like before.
        composeRule.onNode(hasScrollAction()).performScrollToIndex(20)

        composeRule.onNodeWithText("Channel 20").assertIsDisplayed()
        composeRule.onNodeWithText("Channel 1").assertIsNotDisplayed()
        val list = composeRule.onNode(hasScrollAction()).getBoundsInRoot()
        val header = composeRule.onNode(hasText("Doku") and isHeading())
            .assertIsDisplayed()
            .getBoundsInRoot()
        assertEquals(list.top.value, header.top.value, 1f)
    }

    @Test
    fun spacerIsAGapAndKeepsThePinnedHeader() {
        val first = (1..10).map { n ->
            ServiceListItem(n, "1:0:1:$n:1:1:1:0:0:0:", "Channel $n", ServiceRowKind.CHANNEL)
        }
        val second = (12..30).map { n ->
            ServiceListItem(n, "1:0:1:$n:1:1:1:0:0:0:", "Channel $n", ServiceRowKind.CHANNEL)
        }
        val items = listOf(
            ServiceListItem(0, "1:64:1:0:0:0:0:0:0:0::Doku", "Doku", ServiceRowKind.MARKER)
        ) + first + ServiceListItem(
            11,
            "1:832:D:0:0:0:0:0:0:0:",
            "",
            ServiceRowKind.MARKER
        ) + second
        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(items = items, onItemClick = {}, onItemLongClick = {})
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToIndex(20)

        composeRule.onNodeWithText("Channel 20").assertIsDisplayed()
        composeRule.onAllNodes(isHeading()).assertCountEquals(1)
        val list = composeRule.onNode(hasScrollAction()).getBoundsInRoot()
        val header = composeRule.onNode(hasText("Doku") and isHeading())
            .assertIsDisplayed()
            .getBoundsInRoot()
        assertEquals(list.top.value, header.top.value, 1f)
    }

    @Test
    fun showsChannelAndNowNext() {
        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(
                    items = listOf(
                        ServiceListItem(
                            index = 0,
                            reference = "1:0:1:1:1:1:1:0:0:0:",
                            name = "ARD",
                            kind = ServiceRowKind.CHANNEL,
                            nowTitle = "Tagesschau",
                            nowStart = "20:00",
                            nowDuration = "15",
                            nextTitle = "Wetter",
                            nextStart = "20:15",
                            nextDuration = "5",
                            progressMax = 15,
                            progress = 3
                        )
                    ),
                    onItemClick = {},
                    onItemLongClick = {}
                )
            }
        }
        composeRule.onNodeWithText("ARD").assertIsDisplayed()
        // Now/next use separate start | title | end columns (aligned under each other).
        composeRule.onNodeWithText("20:00").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("15").assertIsDisplayed()
        composeRule.onNodeWithText("20:15").assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").assertIsDisplayed()
        composeRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun withoutPiconsChannelTitleSitsAtLeadingEdge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()

        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(
                    items = listOf(
                        ServiceListItem(
                            index = 0,
                            reference = "1:0:1:1:1:1:1:0:0:0:",
                            name = "ZDF",
                            kind = ServiceRowKind.CHANNEL
                        )
                    ),
                    onItemClick = {},
                    onItemLongClick = {}
                )
            }
        }
        // Tile inset 8.dp + ListItem start 16.dp.
        // useUnmergedTree: combinedClickable merges semantics up to the row.
        composeRule.onNodeWithText("ZDF", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(24.dp)
    }

    @Test
    fun rowMenuOpensOnItsRowAndReportsThePickedAction() {
        val ard = ServiceListItem(
            index = 0,
            reference = "1:0:1:1:1:1:1:0:0:0:",
            name = "ARD",
            kind = ServiceRowKind.CHANNEL
        )
        val zdf = ServiceListItem(
            index = 1,
            reference = "1:0:1:2:1:1:1:0:0:0:",
            name = "ZDF",
            kind = ServiceRowKind.CHANNEL
        )
        var menu by mutableStateOf<RowMenuState<ServiceRowAction>?>(null)
        var picked: ServiceRowAction? = null
        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(
                    items = listOf(ard, zdf),
                    onItemClick = { item ->
                        menu = RowMenuState(
                            serviceRowKey(item),
                            listOf(ServiceRowAction.BrowseEpg, ServiceRowAction.Zap)
                        )
                    },
                    onItemLongClick = {},
                    menu = menu,
                    onMenuAction = { picked = it },
                    onMenuDismiss = { menu = null }
                )
            }
        }
        composeRule.onNodeWithText("ZDF").performClick()
        // The menu is its own popup window, so compare on-screen positions.
        val zdfTop = composeRule.onNodeWithText("ZDF", useUnmergedTree = true)
            .fetchSemanticsNode().positionOnScreen.y
        val zap = composeRule.onNodeWithText("Zap").assertIsDisplayed()
        composeRule.onNodeWithText("Browse EPG").assertIsDisplayed()
        val menuTop = composeRule.onNodeWithText("Browse EPG")
            .fetchSemanticsNode().positionOnScreen.y
        assertTrue("expected the menu below the tapped row's title", menuTop > zdfTop)
        zap.performClick()
        composeRule.waitForIdle()
        assertEquals(ServiceRowAction.Zap, picked)
        composeRule.onNodeWithText("Browse EPG").assertDoesNotExist()
    }

    @Test
    fun channelRowsAreInsetTonalTilesWithAGap() {
        composeRule.setContent {
            DreamDroidTheme {
                ServiceListScreen(
                    items = listOf(
                        ServiceListItem(
                            index = 0,
                            reference = "1:0:1:1:1:1:1:0:0:0:",
                            name = "ARD",
                            kind = ServiceRowKind.CHANNEL
                        ),
                        ServiceListItem(
                            index = 1,
                            reference = "1:0:1:2:1:1:1:0:0:0:",
                            name = "ZDF",
                            kind = ServiceRowKind.CHANNEL
                        )
                    ),
                    onItemClick = {},
                    onItemLongClick = {}
                )
            }
        }
        val tiles = composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG)
        tiles.assertCountEquals(2)
        tiles[0].assertLeftPositionInRootIsEqualTo(8.dp)
        val first = tiles[0].getBoundsInRoot()
        val second = tiles[1].getBoundsInRoot()
        val gap = second.top - first.bottom
        assertTrue("expected a gutter between tiles, gap=$gap", gap >= 3.dp)
    }

    @Test
    fun progressStripOmitsTrackAndStopIndicator() {
        var primary = 0
        var track = 0
        composeRule.setContent {
            DreamDroidTheme {
                primary = MaterialTheme.colorScheme.primary.toArgb()
                track = MaterialTheme.colorScheme.secondaryContainer.toArgb()
                ServiceListScreen(
                    items = listOf(
                        ServiceListItem(
                            index = 0,
                            reference = "1:0:1:1:1:1:1:0:0:0:",
                            name = "ARD",
                            kind = ServiceRowKind.CHANNEL,
                            nowTitle = "Tagesschau",
                            nowStart = "20:00",
                            nowDuration = "+20",
                            progressMax = 10,
                            progress = 4
                        )
                    ),
                    onItemClick = {},
                    onItemLongClick = {}
                )
            }
        }
        // combinedClickable on the row merges semantics; capture the bar itself.
        val bitmap = composeRule
            .onNodeWithTag(SERVICE_LIST_PROGRESS_TAG, useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        val xFillTo = (bitmap.width * 0.3f).toInt().coerceIn(1, bitmap.width)
        val xStopFrom = (bitmap.width * 0.9f).toInt().coerceIn(0, bitmap.width - 1)
        var fillHits = 0
        var trackHits = 0
        var stopHits = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < xFillTo) {
                val px = bitmap.getPixel(x, y)
                if (rgbDistance(px, primary) < 40) {
                    fillHits++
                }
                if (rgbDistance(px, track) < 40) {
                    trackHits++
                }
                x++
            }
            x = xStopFrom
            while (x < bitmap.width) {
                if (rgbDistance(bitmap.getPixel(x, y), primary) < 40) {
                    stopHits++
                }
                x++
            }
            y++
        }
        assertTrue(
            "current progress should paint primary pixels " +
                "(fillHits=$fillHits ${bitmap.width}x${bitmap.height} " +
                "primary=#${Integer.toHexString(primary)} " +
                "track=#${Integer.toHexString(track)})",
            fillHits > 10
        )
        assertTrue(
            "filled progress should not show the M3 track under it " +
                "(trackHits=$trackHits fillHits=$fillHits)",
            trackHits == 0
        )
        assertTrue(
            "right edge must not draw the M3 stop indicator (hits=$stopHits)",
            stopHits == 0
        )
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
