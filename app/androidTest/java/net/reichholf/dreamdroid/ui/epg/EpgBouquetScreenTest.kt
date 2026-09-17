package net.reichholf.dreamdroid.ui.epg

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.compose.LIST_ROW_SURFACE_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EpgBouquetScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
    }

    @Test
    fun seededEventsShowFieldsAndClick() {
        val first = Event(
            eventId = "100",
            title = "Tagesschau",
            serviceReference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
            serviceName = "Das Erste HD",
            startReadable = "20:00",
            durationReadable = "15",
            descriptionExtended = "Die Nachrichten."
        )
        val second = Event(
            eventId = "101",
            title = "Wetter",
            serviceReference = "1:0:1:6DCB:44D:1:C00000:0:0:0:",
            serviceName = "ZDF HD",
            startReadable = "20:15",
            durationReadable = "10",
            descriptionExtended = "Der Wetterbericht."
        )
        var clicked: Event? = null
        composeRule.setContent {
            DreamDroidTheme {
                EpgBouquetScreen(
                    items = listOf(first, second),
                    onItemClick = { clicked = it }
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(24.dp)
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("20:00").assertIsDisplayed()
        composeRule.onNodeWithText("15").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten.").assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").assertIsDisplayed().performClick()
        assertEquals(second, clicked)
        composeRule.onAllNodesWithTag(EPG_TIME_JUMP_DATE_CHIP_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(EPG_TIME_JUMP_TIME_CHIP_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(LIST_ROW_SURFACE_TAG).assertCountEquals(2)
    }

    @Test
    fun timeJumpBarShowsDateAndTimeChipsAndActions() {
        var dateClicks = 0
        var timeClicks = 0
        var nowClicks = 0
        var primeClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                EpgBouquetScreen(
                    items = emptyList(),
                    onItemClick = {},
                    emptyMessage = "No items to display…",
                    timeJump = EpgTimeJumpUi(
                        dateLabel = "Sep 13, 2026",
                        timeLabel = "20:15",
                        onPickDate = { dateClicks++ },
                        onPickTime = { timeClicks++ },
                        onNow = { nowClicks++ },
                        onPrime = { primeClicks++ }
                    )
                )
            }
        }
        composeRule.onNodeWithText("Sep 13, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("20:15").assertIsDisplayed()
        composeRule.onNodeWithText("Now").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Prime").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(EPG_TIME_JUMP_DATE_CHIP_TAG).performClick()
        composeRule.onNodeWithTag(EPG_TIME_JUMP_TIME_CHIP_TAG).performClick()
        assertEquals(1, nowClicks)
        assertEquals(1, primeClicks)
        assertEquals(1, dateClicks)
        assertEquals(1, timeClicks)
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                EpgBouquetScreen(
                    items = emptyList(),
                    onItemClick = {},
                    emptyMessage = "No items to display…"
                )
            }
        }
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
    }
}
