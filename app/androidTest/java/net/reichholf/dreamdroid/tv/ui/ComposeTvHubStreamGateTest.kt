package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ComposeTvHubStreamGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offlineServiceClickShowsUnavailableAndDoesNotStream() {
        var streams = 0
        composeRule.setContent {
            streamGateHost(
                streamingEnabled = false,
                onServiceClick = { streams++ },
                service = demoService()
            )
        }
        activateCard("hub_service_card") { overlayVisible() }
        composeRule.waitForIdle()
        assertEquals(0, streams)
        composeRule.onNodeWithTag("hub_stream_unavailable").assertIsDisplayed()
    }

    @Test
    fun onlineServiceClickStreams() {
        var streams = 0
        composeRule.setContent {
            streamGateHost(
                streamingEnabled = true,
                onServiceClick = { streams++ },
                service = demoService()
            )
        }
        activateCard("hub_service_card") { streams > 0 }
        composeRule.waitForIdle()
        assertEquals(1, streams)
        composeRule.onAllNodesWithTag("hub_stream_unavailable").assertCountEquals(0)
    }

    @Test
    fun offlineMovieClickShowsUnavailableAndDoesNotStream() {
        var streams = 0
        composeRule.setContent {
            streamGateHost(
                streamingEnabled = false,
                onMovieClick = { streams++ },
                movie = demoMovie()
            )
        }
        activateCard("hub_movie_card") { overlayVisible() }
        composeRule.waitForIdle()
        assertEquals(0, streams)
        composeRule.onNodeWithTag("hub_stream_unavailable").assertIsDisplayed()
    }

    @Test
    fun onlineMovieClickStreams() {
        var streams = 0
        composeRule.setContent {
            streamGateHost(
                streamingEnabled = true,
                onMovieClick = { streams++ },
                movie = demoMovie()
            )
        }
        activateCard("hub_movie_card") { streams > 0 }
        composeRule.waitForIdle()
        assertEquals(1, streams)
        composeRule.onAllNodesWithTag("hub_stream_unavailable").assertCountEquals(0)
    }

    @Test
    fun chromeOfflineServiceClickDoesNotStream() {
        var streams = 0
        val bouquetRef = "1:7:1:0:0:0:0:0:0:0:Favourites"
        composeRule.setContent {
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(TvComposeHubHost.HEADER_SETTINGS_ID, "Preferences"),
                    HubNavHeader(bouquetRef, "Favourites")
                ),
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
                streamingEnabled = false,
                onServiceClick = { _, _ -> streams++ }
            )
        }
        val cards = composeRule.onAllNodesWithTag("hub_service_card")
        if (cards.fetchSemanticsNodes().isNotEmpty()) {
            activateCard("hub_service_card") { overlayVisible() || streams > 0 }
            composeRule.waitForIdle()
            if (overlayVisible()) {
                composeRule.onNodeWithTag("hub_stream_unavailable").assertExists()
            }
        }
        assertEquals(0, streams)
    }

    @Composable
    private fun streamGateHost(
        streamingEnabled: Boolean,
        onServiceClick: () -> Unit = {},
        onMovieClick: () -> Unit = {},
        service: ServiceNowNext? = null,
        movie: Movie? = null
    ) {
        DreamDroidTvTheme {
            var showUnavailable by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (service != null) {
                    HubServiceRow(
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
                        services = listOf(service),
                        streamingEnabled = streamingEnabled,
                        onServiceClick = { _, _ ->
                            if (!streamingEnabled) {
                                showUnavailable = true
                            } else {
                                onServiceClick()
                            }
                        }
                    )
                }
                if (movie != null) {
                    HubMovieRow(
                        dirname = "/media/hdd/movie",
                        movies = listOf(movie),
                        streamingEnabled = streamingEnabled,
                        onMovieClick = {
                            if (!streamingEnabled) {
                                showUnavailable = true
                            } else {
                                onMovieClick()
                            }
                        }
                    )
                }
                if (showUnavailable) {
                    TvNeedsReceiverOverlay(onDismiss = { showUnavailable = false })
                }
            }
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
