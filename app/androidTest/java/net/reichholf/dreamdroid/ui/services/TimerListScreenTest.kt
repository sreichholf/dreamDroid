package net.reichholf.dreamdroid.ui.services

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TimerListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun showsTimerNameAndService() {
        composeRule.setContent {
            DreamDroidTheme {
                TimerListScreen(
                    items = listOf(
                        TimerListItem(
                            index = 0,
                            name = "Evening news",
                            serviceName = "ARD",
                            begin = "20:00",
                            end = "20:15",
                            action = "Record",
                            state = "Waiting",
                            stateColor = 0
                        )
                    ),
                    onItemClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Evening news").assertIsDisplayed()
        composeRule.onNodeWithText("ARD").assertIsDisplayed()
        composeRule.onNodeWithText("20:00 – 20:15").assertIsDisplayed()
    }

    @Test
    fun tapInvokesItemClick() {
        var clicked = -1
        composeRule.setContent {
            DreamDroidTheme {
                TimerListScreen(
                    items = listOf(
                        TimerListItem(
                            index = 0,
                            name = "Evening news",
                            serviceName = "ARD",
                            begin = "20:00",
                            end = "20:15",
                            action = "Record",
                            state = "Waiting",
                            stateColor = 0
                        )
                    ),
                    onItemClick = { clicked = it.index }
                )
            }
        }
        composeRule.onNode(hasText("Evening news") and hasClickAction()).performClick()
        composeRule.waitForIdle()
        assertEquals(0, clicked)
    }

    @Test
    fun stateIndicatorSpansTheTileHeight() {
        var stateColor = 0
        composeRule.setContent {
            DreamDroidTheme {
                stateColor = MaterialTheme.colorScheme.tertiary.toArgb()
                TimerListScreen(
                    items = listOf(
                        TimerListItem(
                            index = 0,
                            name = "Evening news",
                            serviceName = "ARD",
                            begin = "20:00",
                            end = "20:15",
                            action = "Record",
                            state = "Waiting",
                            stateColor = 0
                        )
                    ),
                    onItemClick = {}
                )
            }
        }
        val tile = composeRule.onNodeWithTag(LIST_ROW_TAG).captureToImage().asAndroidBitmap()
        val barWidth = with(composeRule.density) { 4.dp.roundToPx() }
        assertTrue("expected a full-row tile, height=${tile.height}", tile.height > barWidth * 10)
        // Rounded corners clip the bar at the very top and bottom; sample the rest.
        for (y in listOf(tile.height / 4, tile.height / 2, tile.height * 3 / 4)) {
            val inBar = tile.getPixel(barWidth / 2, y)
            val pastBar = tile.getPixel(barWidth * 3, y)
            assertTrue("bar missing at y=$y", rgbDistance(inBar, stateColor) < 40)
            assertTrue("bar too wide at y=$y", rgbDistance(pastBar, stateColor) >= 40)
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
