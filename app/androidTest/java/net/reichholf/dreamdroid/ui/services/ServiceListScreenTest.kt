package net.reichholf.dreamdroid.ui.services

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
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
        // Card padding 12dp + list horizontal padding 8dp = 20dp from root.
        // useUnmergedTree: combinedClickable merges semantics up to the Card (left=8dp).
        composeRule.onNodeWithText("ZDF", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(20.dp)
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
        // Second row must not report the fragment-root origin (0,0) used by the old PopupMenu bug.
        assertTrue("expected tapX >= 0, got $tapX", tapX >= 0)
        assertTrue("expected second-row tapY > 0, got $tapY", tapY > 0)
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
        val bitmap = composeRule
            .onNodeWithTag(SERVICE_LIST_PROGRESS_TAG)
            .captureToImage()
            .asAndroidBitmap()
        val yMid = bitmap.height / 2
        val xFill = (bitmap.width * 0.2f).toInt().coerceIn(0, bitmap.width - 1)
        val fillPx = bitmap.getPixel(xFill, yMid)
        assertTrue(
            "current progress should be primary, not the M3 track under it " +
                "(fill=#${Integer.toHexString(fillPx)} primary=#${Integer.toHexString(primary)} " +
                "track=#${Integer.toHexString(track)})",
            rgbDistance(fillPx, primary) < 40 &&
                rgbDistance(fillPx, primary) < rgbDistance(fillPx, track)
        )
        val xStopFrom = (bitmap.width * 0.9f).toInt().coerceIn(0, bitmap.width - 1)
        var stopHits = 0
        var y = 0
        while (y < bitmap.height) {
            var x = xStopFrom
            while (x < bitmap.width) {
                if (rgbDistance(bitmap.getPixel(x, y), primary) < 40) {
                    stopHits++
                }
                x++
            }
            y++
        }
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
