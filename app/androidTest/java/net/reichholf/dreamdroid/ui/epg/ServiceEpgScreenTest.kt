package net.reichholf.dreamdroid.ui.epg

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
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
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit().putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1").commit()
    }

    @Test
    fun seededEventsShowFieldsAndClick() {
        val first = Event(
            eventId = "100",
            title = "Tagesschau",
            startReadable = "20:00",
            durationReadable = "15",
            descriptionExtended = "Die Nachrichten.",
        )
        val second = Event(
            eventId = "101",
            title = "Wetter",
            startReadable = "20:15",
            durationReadable = "10",
            descriptionExtended = "Der Wetterbericht.",
        )
        var clicked: Event? = null
        composeRule.setContent {
            DreamDroidTheme {
                ServiceEpgScreen(
                    items = listOf(first, second),
                    onItemClick = { clicked = it },
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("20:00").assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").assertIsDisplayed().performClick()
        assertEquals(second, clicked)
    }

    @Test
    fun emptyStateShowsMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                ServiceEpgScreen(
                    items = emptyList(),
                    onItemClick = {},
                    emptyMessage = "No items to display…",
                )
            }
        }
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
    }
}
