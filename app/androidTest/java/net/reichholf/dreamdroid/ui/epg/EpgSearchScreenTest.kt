package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EpgSearchScreenTest {
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
    fun emptyQueryShowsHint() {
        composeRule.setContent {
            DreamDroidTheme {
                EpgSearchScreen(
                    queryState = TextFieldState(),
                    onSearch = {},
                    expanded = true,
                    onExpandedChange = {},
                    items = emptyList(),
                    onItemClick = {}
                )
            }
        }
        composeRule.onNodeWithText("Search EPG").assertIsDisplayed()
        composeRule.onNodeWithTag(EPG_SEARCH_FIELD_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun imeSearchSubmitsQuery() {
        var submitted: String? = null
        composeRule.setContent {
            DreamDroidTheme {
                EpgSearchScreen(
                    queryState = rememberTextFieldState(),
                    onSearch = { submitted = it },
                    expanded = true,
                    onExpandedChange = {},
                    items = emptyList(),
                    onItemClick = {}
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Tagesschau")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertEquals("Tagesschau", submitted)
    }

    @Test
    fun clearTrailingIconClearsQuery() {
        val query = TextFieldState("Tagesschau")
        composeRule.setContent {
            DreamDroidTheme {
                EpgSearchScreen(
                    queryState = query,
                    onSearch = {},
                    expanded = true,
                    onExpandedChange = {},
                    items = emptyList(),
                    onItemClick = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("", query.text.toString()) }
    }

    @Test
    fun collapsedResultsShowEventsAndClick() {
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
                EpgSearchScreen(
                    queryState = TextFieldState("news"),
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    items = listOf(first, second),
                    onItemClick = { clicked = it }
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").assertIsDisplayed().performClick()
        assertEquals(second, clicked)
    }

    @Test
    fun collapsedEmptyStateShowsMessage() {
        composeRule.setContent {
            DreamDroidTheme {
                EpgSearchScreen(
                    queryState = TextFieldState("none"),
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    items = emptyList(),
                    onItemClick = {},
                    emptyMessage = "No items to display…"
                )
            }
        }
        composeRule.onNodeWithText("No items to display…").assertIsDisplayed()
    }
}
