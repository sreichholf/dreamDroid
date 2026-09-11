package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.enigma.Movie
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ComposeTvHubMovieRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun movieRowClickInvokesCallback() {
        var clicked: Movie? = null
        val movie = Movie(
            reference = "1:0:0:0:0:0:0:0:0:0:",
            title = "Demo Recording",
            description = "Short desc",
            descriptionExtended = "Line1\\nLine2",
            fileName = "demo.ts",
        )
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            ) {
                HubMovieRow(
                    dirname = "/hdd/movie",
                    movies = listOf(movie),
                    onMovieClick = { clicked = it },
                )
            }
        }
        val node = composeRule.onNodeWithTag("hub_movie_card")
        node.assertIsDisplayed().assertHasClickAction()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        if (clicked == null) {
            node.performClick()
        }
        assertEquals(movie, clicked)
    }

    @Test
    fun movieHeaderSelectionShowsMovieRowHost() {
        val movie = Movie(
            reference = "1:0:0:0:0:0:0:0:0:1:",
            title = "Another",
            fileName = "another.ts",
        )
        val headerId = TvComposeHubHost.movieHeaderId("/hdd/movie")
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(headerId, "/hdd/movie"),
                ),
                selectedHeaderId = headerId,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                moviesByLocation = mapOf("/hdd/movie" to listOf(movie)),
            )
        }
        composeRule.onNodeWithTag("compose_tv_hub_rows", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_movie_row", useUnmergedTree = true).assertExists()
    }
}
