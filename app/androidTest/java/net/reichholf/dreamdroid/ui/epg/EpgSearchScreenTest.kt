package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
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
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class EpgSearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
    }

    @Test
    fun emptyFieldWithoutHistoryPromptsAndFocuses() {
        setScreen(queryState = TextFieldState(), focusOnStart = true)

        composeRule.onNodeWithText(context.getString(R.string.epg_search_hint)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.epg_search_prompt))
            .assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.onNodeWithContentDescription(context.getString(R.string.close))
            .assertDoesNotExist()
    }

    @Test
    fun recentSearchesSearchAndRemove() {
        var searched: String? = null
        var removed: String? = null
        setScreen(
            state = EpgSearchUiState(recentSearches = listOf("Tatort", "Tagesschau")),
            onRecentClick = { searched = it },
            onRecentRemove = { removed = it }
        )

        composeRule.onNodeWithText(context.getString(R.string.epg_search_recent))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").performClick()
        composeRule.onNodeWithText("Tatort").assertIsDisplayed()
        composeRule.onAllNodes(
            hasContentDescription(context.getString(R.string.epg_search_recent_remove))
        )[0].performClick()
        composeRule.runOnIdle {
            assertEquals("Tagesschau", searched)
            assertEquals("Tatort", removed)
        }
    }

    @Test
    fun typingAndImeSearchReachTheCallbacks() {
        var submitted = 0
        lateinit var query: TextFieldState
        composeRule.setContent {
            DreamDroidTheme {
                query = rememberTextFieldState()
                EpgSearchScreen(
                    queryState = query,
                    state = EpgSearchUiState(),
                    onBack = {},
                    onSearch = { submitted += 1 },
                    onRecentClick = {},
                    onRecentRemove = {},
                    onItemClick = {}
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Tagesschau")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        composeRule.runOnIdle {
            assertEquals("Tagesschau", query.text.toString())
            assertEquals(1, submitted)
        }
    }

    @Test
    fun backAndClearIcons() {
        var back = 0
        val query = TextFieldState("Tagesschau")
        setScreen(queryState = query, onBack = { back += 1 })

        composeRule.onNodeWithContentDescription(context.getString(R.string.epg_search_back))
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.close))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, back)
            assertEquals("", query.text.toString())
        }
    }

    @Test
    fun resultsShowDayHeadersAndClick() {
        val first = event("100", "Tagesschau", "Das Erste HD", "20:00")
        val second = event("101", "Wetter", "ZDF HD", "20:15")
        var clicked: Event? = null
        setScreen(
            queryState = TextFieldState("news"),
            state = results(
                EpgDaySection(UiText.Resource(R.string.today), listOf(first)),
                EpgDaySection(UiText.Raw("Friday, Jan 4, 2030"), listOf(second))
            ),
            onItemClick = { clicked = it }
        )

        composeRule.onNodeWithText(context.getString(R.string.today)).assertIsDisplayed()
        composeRule.onNodeWithText("Friday, Jan 4, 2030").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("20:00", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").performClick()
        composeRule.runOnIdle { assertEquals(second, clicked) }
    }

    @Test
    fun searchingShowsProgressAndCachedResultsTheOfflineHint() {
        setScreen(
            queryState = TextFieldState("news"),
            state = results().copy(searching = true, cached = true)
        )

        composeRule.onNodeWithTag(EPG_SEARCH_PROGRESS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.epg_search_cached_hint))
            .assertIsDisplayed()
    }

    @Test
    fun emptyResultsShowTheMessage() {
        setScreen(
            queryState = TextFieldState("none"),
            state = results().copy(
                emptyMessage = UiText.Resource(R.string.epg_search_no_cached_match)
            )
        )

        composeRule.onNodeWithText(context.getString(R.string.epg_search_no_cached_match))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(EPG_SEARCH_PROGRESS_TAG).assertDoesNotExist()
    }

    @Test
    fun failedSearchOffersRetry() {
        var retried = 0
        composeRule.setContent {
            DreamDroidTheme {
                EpgSearchScreen(
                    queryState = TextFieldState("news"),
                    state = results().copy(emptyMessage = UiText.Raw("Timeout"), retryable = true),
                    onBack = {},
                    onSearch = {},
                    onRecentClick = {},
                    onRecentRemove = {},
                    onItemClick = {},
                    onRetry = { retried += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Timeout").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reload)).performClick()
        composeRule.runOnIdle { assertEquals(1, retried) }
    }

    private fun setScreen(
        queryState: TextFieldState = TextFieldState(),
        state: EpgSearchUiState = EpgSearchUiState(),
        onBack: () -> Unit = {},
        onRecentClick: (String) -> Unit = {},
        onRecentRemove: (String) -> Unit = {},
        onItemClick: (Event) -> Unit = {},
        focusOnStart: Boolean = false
    ) {
        composeRule.setContent {
            DreamDroidTheme {
                EpgSearchScreen(
                    queryState = queryState,
                    state = state,
                    onBack = onBack,
                    onSearch = {},
                    onRecentClick = onRecentClick,
                    onRecentRemove = onRecentRemove,
                    onItemClick = onItemClick,
                    focusOnStart = focusOnStart
                )
            }
        }
    }

    private fun results(vararg sections: EpgDaySection) =
        EpgSearchUiState(showRecent = false, sections = sections.toList())

    private fun event(id: String, title: String, service: String, time: String) = Event(
        eventId = id,
        title = title,
        start = id,
        serviceReference = "1:0:1:$id:44D:1:C00000:0:0:0:",
        serviceName = service,
        startReadable = "Mon, 01.01. $time",
        startTimeReadable = time,
        durationReadable = "15"
    )
}
