package net.reichholf.dreamdroid.ui.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HubMovieListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

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

    private fun show(
        initial: HubMovieListUiState,
        onItemClick: (MovieListItem, Boolean) -> Unit = { _, _ -> },
        onTagsPicked: (List<Int>) -> Unit = {},
        onDeleteConfirm: () -> Unit = {}
    ) {
        composeRule.setContent {
            var state by remember { mutableStateOf(initial) }
            DreamDroidTheme {
                HubMovieListScreen(
                    state = state,
                    onRefresh = {},
                    onItemClick = onItemClick,
                    onMenuAction = {},
                    onMenuDismiss = {},
                    onDetailDismiss = {},
                    onTagsPicked = onTagsPicked,
                    onTagPickerDismiss = { state = state.copy(tagPicker = null) },
                    onDeleteConfirm = onDeleteConfirm,
                    onDeleteDismiss = { state = state.copy(deleteConfirm = null) }
                )
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
    }
}
