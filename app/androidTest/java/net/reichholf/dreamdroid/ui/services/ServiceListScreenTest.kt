package net.reichholf.dreamdroid.ui.services

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_SURFACE_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
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
                    onItemClick = { _, _, _ -> },
                    onItemLongClick = { _, _, _ -> }
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
                    onItemClick = { _, _, _ -> },
                    onItemLongClick = { _, _, _ -> }
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
    fun channelTapReportsWindowPositionOfRowNotOrigin() {
        var tapX = -1
        var tapY = -1
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
                    onItemClick = { _, x, y ->
                        tapX = x
                        tapY = y
                    },
                    onItemLongClick = { _, _, _ -> }
                )
            }
        }
        composeRule.onNodeWithText("ZDF").performClick()
        composeRule.waitForIdle()
        val zdf = composeRule.onNodeWithText("ZDF", useUnmergedTree = true).getBoundsInRoot()
        val tapXDp = with(composeRule.density) { tapX.toDp() }
        val tapYDp = with(composeRule.density) { tapY.toDp() }
        assertTrue("expected tapX > 0 (not origin), got $tapXDp", tapXDp > 0.dp)
        assertTrue(
            "expected second-row tapY near the ZDF tile, tapY=$tapYDp tile=$zdf",
            tapYDp > 40.dp && tapYDp <= zdf.bottom
        )
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
                    onItemClick = { _, _, _ -> },
                    onItemLongClick = { _, _, _ -> }
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
                    onItemClick = { _, _, _ -> },
                    onItemLongClick = { _, _, _ -> }
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
