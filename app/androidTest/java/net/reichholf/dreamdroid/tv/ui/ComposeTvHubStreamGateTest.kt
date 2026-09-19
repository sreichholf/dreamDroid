package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ComposeTvHubStreamGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chromeOfflineServiceClickShowsUnavailableAndDoesNotStream() {
        var streams = 0
        composeRule.setContent {
            chromeStreamHost(
                streamingEnabled = false,
                onServiceClick = { streams++ }
            )
        }
        activateCard("hub_service_card") { overlayVisible() }
        composeRule.waitForIdle()
        assertEquals(0, streams)
        composeRule.onNodeWithTag("hub_stream_unavailable").assertIsDisplayed()
    }

    @Test
    fun chromeOnlineServiceClickStreams() {
        var streams = 0
        composeRule.setContent {
            chromeStreamHost(
                streamingEnabled = true,
                onServiceClick = { streams++ }
            )
        }
        activateCard("hub_service_card") { streams > 0 }
        composeRule.waitForIdle()
        assertEquals(1, streams)
        composeRule.onAllNodesWithTag("hub_stream_unavailable").assertCountEquals(0)
    }

    @Test
    fun chromeOfflineMovieClickShowsUnavailableAndDoesNotStream() {
        var streams = 0
        composeRule.setContent {
            chromeMovieHost(
                streamingEnabled = false,
                onMovieClick = { streams++ }
            )
        }
        activateCard("hub_movie_card") { overlayVisible() }
        composeRule.waitForIdle()
        assertEquals(0, streams)
        composeRule.onNodeWithTag("hub_stream_unavailable").assertIsDisplayed()
    }

    @Test
    fun chromeOnlineMovieClickStreams() {
        var streams = 0
        composeRule.setContent {
            chromeMovieHost(
                streamingEnabled = true,
                onMovieClick = { streams++ }
            )
        }
        activateCard("hub_movie_card") { streams > 0 }
        composeRule.waitForIdle()
        assertEquals(1, streams)
        composeRule.onAllNodesWithTag("hub_stream_unavailable").assertCountEquals(0)
    }

    @Composable
    private fun chromeStreamHost(streamingEnabled: Boolean, onServiceClick: () -> Unit) {
        val bouquetRef = "1:7:1:0:0:0:0:0:0:0:Favourites"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            ComposeTvHubChrome(
                headers = listOf(HubNavHeader(bouquetRef, "Favourites")),
                selectedHeaderId = bouquetRef,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = listOf(
                    HubBouquetRow(
                        bouquet = Service(bouquetRef, "Favourites"),
                        services = listOf(demoService())
                    )
                ),
                streamingEnabled = streamingEnabled,
                onServiceClick = { _, _ -> onServiceClick() }
            )
        }
    }

    @Composable
    private fun chromeMovieHost(streamingEnabled: Boolean, onMovieClick: () -> Unit) {
        val headerId = TvComposeHubHost.movieHeaderId("/media/hdd/movie")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            ComposeTvHubChrome(
                headers = listOf(HubNavHeader(headerId, "/media/hdd/movie")),
                selectedHeaderId = headerId,
                onHeaderSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                moviesByLocation = mapOf("/media/hdd/movie" to listOf(demoMovie())),
                streamingEnabled = streamingEnabled,
                onMovieClick = { onMovieClick() }
            )
        }
    }

    private fun activateCard(tag: String, done: () -> Boolean) {
        val node = composeRule.onNodeWithTag(tag)
        node.assertIsDisplayed()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        if (!done()) {
            node.performClick()
            composeRule.waitForIdle()
        }
    }

    private fun overlayVisible(): Boolean = composeRule.onAllNodesWithTag("hub_stream_unavailable")
        .fetchSemanticsNodes()
        .isNotEmpty()

    private fun demoService() = ServiceNowNext(
        serviceReference = "1:0:1:1:1:1:1:0:0:0:",
        serviceName = "Demo Channel",
        now = Event(title = "Now Show")
    )

    private fun demoMovie() = Movie(
        reference = "1:0:0:0:0:0:0:0:0:0:",
        title = "Demo Recording",
        fileName = "demo.ts"
    )
}
