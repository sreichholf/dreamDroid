package net.reichholf.dreamdroid.ui.services

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.testutil.COMPACT_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.EXPANDED_WINDOW_WIDTH
import net.reichholf.dreamdroid.testutil.WithWindowSize
import net.reichholf.dreamdroid.ui.compose.LIST_DETAIL_DETAIL_PANE_TAG
import net.reichholf.dreamdroid.ui.movies.MOVIE_DETAIL_CAPPED_TAG
import net.reichholf.dreamdroid.ui.movies.MOVIE_DETAIL_UNCAPPED_TAG
import net.reichholf.dreamdroid.ui.movies.MovieDetailContent
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HubMovieListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptyListShowsMessage() {
        show(HubMovieListUiState(HDD, emptyMessage = UiText.Resource(R.string.no_list_item)))

        composeRule.onNodeWithText(context.getString(R.string.no_list_item)).assertIsDisplayed()
    }

    @Test
    fun rowTapsReportShortAndLongClicks() {
        val clicks = mutableListOf<Pair<Int, Boolean>>()
        show(HubMovieListUiState(HDD, items = listOf(item(0, "News"))), onItemClick = { i, l ->
            clicks += i.index to l
        })

        composeRule.onNodeWithText("News").performClick()

        assertEquals(listOf(0 to false), clicks)
    }

    @Test
    fun deleteConfirmNamesTheRecording() {
        var confirmed = false
        show(
            HubMovieListUiState(HDD, items = listOf(item(0, "News")), deleteConfirm = "News"),
            onDeleteConfirm = { confirmed = true }
        )

        composeRule.onNodeWithText(context.getString(R.string.delete_confirm)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.delete)).performClick()

        assertTrue(confirmed)
    }

    @Test
    fun tagPickerChecksSelectedTags() {
        var picked: List<Int>? = null
        show(
            HubMovieListUiState(
                HDD,
                items = listOf(item(0, "News")),
                selectedTags = listOf("sports"),
                tagPicker = listOf("news", "sports")
            ),
            onTagsPicked = { picked = it }
        )

        composeRule.onNodeWithText(context.getString(R.string.choose_tags)).assertIsDisplayed()
        composeRule.onNodeWithText("sports").assertIsOn()
        composeRule.onNodeWithText("news").performClick()
        composeRule.onNodeWithText(context.getString(R.string.ok)).performClick()

        assertEquals(listOf(0, 1), picked?.sorted())
    }

    @Test
    fun twoPaneWindowShowsInfoBesideTheList() {
        show(
            HubMovieListUiState(HDD, items = listOf(item(0, "News")), detail = DETAIL),
            width = { EXPANDED_WINDOW_WIDTH }
        )

        // The detail repeats the title; the row is the clickable one.
        val row = composeRule.onNode(hasText("News") and hasClickAction())
            .fetchSemanticsNode().boundsInRoot
        val pane = composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("detail pane starts after the row", pane.left >= row.right)
        composeRule.onNodeWithTag(MOVIE_DETAIL_UNCAPPED_TAG).assertExists()
        composeRule.onNodeWithText(DETAIL.descriptionExtended).assertExists()
    }

    @Test
    fun twoPaneWindowWithoutInfoShowsPlaceholder() {
        show(
            HubMovieListUiState(HDD, items = listOf(item(0, "News"))),
            width = { EXPANDED_WINDOW_WIDTH }
        )

        composeRule.onNodeWithText(context.getString(R.string.movie_detail_pane_empty))
            .assertExists()
    }

    @Test
    fun twoPaneBackClearsTheInfo() {
        var dismissed = false
        show(
            HubMovieListUiState(HDD, items = listOf(item(0, "News")), detail = DETAIL),
            width = { EXPANDED_WINDOW_WIDTH },
            onDetailDismiss = { dismissed = true }
        )

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun listKeepsItsScrollWhenTheWindowGainsADetailPane() {
        var width by mutableStateOf(COMPACT_WINDOW_WIDTH)
        show(
            HubMovieListUiState(HDD, items = List(60) { item(it, "Movie $it") }),
            width = { width }
        )
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(40)
        composeRule.onNodeWithText("Movie 40").assertIsDisplayed()

        width = EXPANDED_WINDOW_WIDTH
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Movie 40").assertIsDisplayed()
        composeRule.onNodeWithText("Movie 0").assertDoesNotExist()
    }

    @Test
    fun compactWindowShowsInfoInASheet() {
        show(HubMovieListUiState(HDD, items = listOf(item(0, "News")), detail = DETAIL))

        composeRule.onNodeWithTag(LIST_DETAIL_DETAIL_PANE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(MOVIE_DETAIL_CAPPED_TAG).assertIsDisplayed()
    }

    private fun show(
        initial: HubMovieListUiState,
        width: () -> Dp = { COMPACT_WINDOW_WIDTH },
        onDetailDismiss: () -> Unit = {},
        onItemClick: (MovieListItem, Boolean) -> Unit = { _, _ -> },
        onTagsPicked: (List<Int>) -> Unit = {},
        onDeleteConfirm: () -> Unit = {}
    ) {
        composeRule.setContent {
            var state by remember { mutableStateOf(initial) }
            DreamDroidTheme {
                WithWindowSize(width()) {
                    HubMovieListScreen(
                        state = state,
                        onRefresh = {},
                        onItemClick = onItemClick,
                        onMenuAction = {},
                        onMenuDismiss = {},
                        onDetailDismiss = onDetailDismiss,
                        onTagsPicked = onTagsPicked,
                        onTagPickerDismiss = { state = state.copy(tagPicker = null) },
                        onDeleteConfirm = onDeleteConfirm,
                        onDeleteDismiss = { state = state.copy(deleteConfirm = null) }
                    )
                }
            }
        }
    }

    private fun item(index: Int, title: String) = MovieListItem(
        index = index,
        title = title,
        serviceName = "ZDF",
        fileSize = "",
        time = "",
        length = ""
    )

    private companion object {
        const val HDD = "/media/hdd/movie"
        val DETAIL = MovieDetailContent(
            title = "News",
            serviceName = "ZDF",
            description = "Evening news",
            descriptionExtended = "Reports from the day",
            tags = emptyList(),
            length = "30 min",
            date = "",
            fileSize = ""
        )
    }
}
