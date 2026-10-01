package net.reichholf.dreamdroid.ui.epg

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ServiceEpgScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun seededEventsShowFieldsAndClick() {
        val first = Event(
            eventId = "100",
            title = "Tagesschau",
            startReadable = "Mon, 01.01. 20:00",
            startTimeReadable = "20:00",
            durationReadable = "15",
            descriptionExtended = "Die Nachrichten."
        )
        val second = Event(
            eventId = "101",
            title = "Wetter",
            startReadable = "Tue, 02.01. 20:15",
            startTimeReadable = "20:15",
            durationReadable = "10",
            descriptionExtended = "Der Wetterbericht."
        )
        var clicked: Event? = null
        composeRule.setContent {
            DreamDroidTheme {
                ServiceEpgScreen(
                    sections = listOf(
                        EpgDaySection(UiText.Resource(R.string.today), listOf(first)),
                        EpgDaySection(UiText.Resource(R.string.tomorrow), listOf(second))
                    ),
                    onItemClick = { clicked = it }
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.today)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.tomorrow)).assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertLeftPositionInRootIsEqualTo(24.dp)
        composeRule.onNodeWithText("20:00").assertIsDisplayed()
        composeRule.onNodeWithText("Mon, 01.01. 20:00").assertDoesNotExist()
        composeRule.onNode(
            hasText("Wetter") and hasClickAction()
        ).assertIsDisplayed().performClick()
        assertEquals(second, clicked)
    }

    @Test
    fun emptyStateShowsMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                ServiceEpgScreen(
                    sections = emptyList(),
                    onItemClick = {},
                    emptyMessage = "No items to display…"
                )
            }
        }
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
    }
}
