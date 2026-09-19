package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.tv.view.FITTED_ELLIPSIS_TEXT_TAG
import net.reichholf.dreamdroid.tv.view.FittedMaxLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            fileName = "demo.ts"
        )
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                HubMovieRow(
                    dirname = "/hdd/movie",
                    movies = listOf(movie),
                    onMovieClick = { clicked = it }
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
            fileName = "another.ts"
        )
        val headerId = TvComposeHubHost.movieHeaderId("/hdd/movie")
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(headerId, "/hdd/movie")
                ),
                selectedHeaderId = headerId,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                moviesByLocation = mapOf("/hdd/movie" to listOf(movie))
            )
        }
        composeRule.onNodeWithTag("compose_tv_hub_rows", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("hub_movie_row", useUnmergedTree = true).assertExists()
    }

    @Test
    fun twoLineTitleFitsFewerBodyLinesThanOneLineTitle() {
        val longBody = List(40) { "Zwischen Menopause und mutigen Neustarts" }
            .joinToString(" ")
        composeRule.setContent {
            Column(modifier = Modifier.fillMaxWidth()) {
                HubMovieRow(
                    dirname = "/hdd/one",
                    movies = listOf(
                        Movie(
                            reference = "1:0:0:0:0:0:0:0:0:3:",
                            title = "Short",
                            descriptionExtended = longBody,
                            fileName = "one.ts"
                        )
                    ),
                    onMovieClick = {}
                )
                HubMovieRow(
                    dirname = "/hdd/two",
                    movies = listOf(
                        Movie(
                            reference = "1:0:0:0:0:0:0:0:0:4:",
                            title = "First title line\nSecond title line",
                            descriptionExtended = longBody,
                            fileName = "two.ts"
                        )
                    ),
                    onMovieClick = {}
                )
            }
        }
        composeRule.waitForIdle()
        val nodes = composeRule
            .onAllNodesWithTag(FITTED_ELLIPSIS_TEXT_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(2, nodes.size)
        val oneLine = checkNotNull(nodes[0].config.getOrNull(FittedMaxLines)) {
            "one-line title fittedMaxLines missing"
        }
        val twoLine = checkNotNull(nodes[1].config.getOrNull(FittedMaxLines)) {
            "two-line title fittedMaxLines missing"
        }
        assertTrue(
            "two-line title body lines ($twoLine) should be below one-line ($oneLine)",
            twoLine < oneLine
        )
        assertTrue("body should keep at least one full line, was $twoLine", twoLine >= 1)
    }

    @Test
    fun longTitleStaysOnCardAndBodyUsesFittedLines() {
        val title = List(20) { "Freiheitsbooster" }.joinToString(" ")
        setMovieRow(
            Movie(
                reference = "1:0:0:0:0:0:0:0:0:2:",
                title = title,
                descriptionExtended = List(40) { "Details" }.joinToString(" "),
                fileName = "long.ts"
            )
        )
        composeRule
            .onNodeWithTag("hub_movie_card_title", useUnmergedTree = true)
            .assertIsDisplayed()
        val lines = bodyMaxLinesFromTree()
        assertTrue("fitted body lines=$lines", lines >= 1)
    }

    private fun setMovieRow(movie: Movie) {
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                HubMovieRow(
                    dirname = "/hdd/movie",
                    movies = listOf(movie),
                    onMovieClick = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun bodyMaxLinesFromTree(): Int {
        val node = composeRule
            .onNodeWithTag(FITTED_ELLIPSIS_TEXT_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
        val lines = node.config.getOrNull(FittedMaxLines)
        assertTrue("fittedMaxLines missing on body text", lines != null)
        return lines!!
    }
}
