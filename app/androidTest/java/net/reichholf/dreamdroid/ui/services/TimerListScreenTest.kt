package net.reichholf.dreamdroid.ui.services

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_SURFACE_TAG
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
        composeRule.onNodeWithText("Evening news").performClick()
        composeRule.waitForIdle()
        assertEquals(0, clicked)
    }

    @Test
    fun stateIndicatorSpansTheTileHeight() {
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
        val tile = composeRule.onNodeWithTag(LIST_ROW_SURFACE_TAG).getBoundsInRoot()
        val bar = composeRule
            .onNodeWithTag(TIMER_LIST_STATE_TAG, useUnmergedTree = true)
            .getBoundsInRoot()
        val tileHeight = tile.bottom - tile.top
        val barHeight = bar.bottom - bar.top
        assertEquals(tile.left, bar.left)
        assertEquals(tile.top, bar.top)
        assertEquals(tile.bottom, bar.bottom)
        assertEquals(tileHeight, barHeight)
        assertTrue("expected a full-row bar, height=$barHeight", barHeight > 40.dp)
    }
}
