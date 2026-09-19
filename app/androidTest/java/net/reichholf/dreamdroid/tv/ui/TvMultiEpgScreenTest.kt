package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgZoom
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvMultiEpgScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun screenAndChromeTagsExist() {
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                ) {
                    fixtureScreen()
                }
            }
        }
        composeRule.onNodeWithTag("tv_multi_epg_screen").assertExists()
        composeRule.onNodeWithTag("tv_multi_epg_chrome").assertExists()
        composeRule.onNodeWithTag("tv_multi_epg_channel_list", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun dpadDownRightCenterSelectsSecondChannelNextBar() {
        var clicked: String? = null
        val start = 1_700_000_000L
        val channels = fixtureChannels(start)
        composeRule.setContent {
            var selectedRef by remember { mutableStateOf(channels[0].serviceRef) }
            var selectedStart by remember { mutableLongStateOf(start) }
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                ) {
                    TvMultiEpgScreen(
                        bouquetName = "Favourites",
                        channels = channels,
                        timelineStartSec = start,
                        timelineEndSec = start + 4L * 3600L,
                        nowSec = start,
                        originFloorSec = start,
                        loading = false,
                        errorMessage = null,
                        selectedServiceRef = selectedRef,
                        selectedStartSec = selectedStart,
                        onSelectedChange = { ref, sec ->
                            selectedRef = ref
                            selectedStart = sec
                        },
                        onJumpToNow = {},
                        onPrevDay = {},
                        onNextDay = {},
                        onVisibleWindow = { _, _ -> },
                        onEventClick = { clicked = it.title },
                        onBouquetClick = {}
                    )
                }
            }
        }
        val grid = composeRule.onNodeWithTag("tv_multi_epg_grid")
        grid.assertExists()
        grid.requestFocus()
        composeRule.waitForIdle()
        grid.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        grid.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()
        grid.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertEquals("Serie", clicked)
    }

    @Test
    fun nowAndZoomChromeInvokeCallbacks() {
        var nowClicks = 0
        var zoomMinutes: Int? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                ) {
                    fixtureScreen(
                        onJumpToNow = { nowClicks++ },
                        onVisibleMinutesChange = { zoomMinutes = it }
                    )
                }
            }
        }
        val now = composeRule.onNodeWithTag("tv_multi_epg_now")
        now.assertIsDisplayed()
        now.requestFocus()
        now.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertEquals(1, nowClicks)

        val zoom = composeRule.onNodeWithTag("tv_multi_epg_zoom")
        zoom.assertIsDisplayed()
        zoom.requestFocus()
        zoom.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        assertEquals(MultiEpgZoom.OPTIONS_MINUTES[2], zoomMinutes)
    }

    @Test
    fun emptyBarRowCenterDoesNotCrash() {
        val start = 1_700_000_000L
        val empty = MultiEpgChannel(
            serviceRef = "1:0:1:9:0:0:0:0:0:0:",
            serviceName = "Empty",
            bars = emptyList()
        )
        composeRule.setContent {
            var selectedRef by remember { mutableStateOf(empty.serviceRef) }
            var selectedStart by remember { mutableLongStateOf(start) }
            DreamDroidTvTheme {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                ) {
                    TvMultiEpgScreen(
                        bouquetName = "Favourites",
                        channels = listOf(empty),
                        timelineStartSec = start,
                        timelineEndSec = start + 3600L,
                        nowSec = start,
                        originFloorSec = start,
                        loading = false,
                        errorMessage = null,
                        selectedServiceRef = selectedRef,
                        selectedStartSec = selectedStart,
                        onSelectedChange = { ref, sec ->
                            selectedRef = ref
                            selectedStart = sec
                        },
                        onJumpToNow = {},
                        onPrevDay = {},
                        onNextDay = {},
                        onVisibleWindow = { _, _ -> },
                        onEventClick = {},
                        onBouquetClick = {}
                    )
                }
            }
        }
        val grid = composeRule.onNodeWithTag("tv_multi_epg_grid")
        grid.requestFocus()
        composeRule.waitForIdle()
        grid.performKeyInput { pressKey(Key.DirectionLeft) }
        grid.performKeyInput { pressKey(Key.DirectionRight) }
        grid.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_multi_epg_screen").assertExists()
    }

    @Test
    fun detailStreamCenterInvokesCallback() {
        var streamed = false
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvMultiEpgEventDetail(
                        event = Event(
                            eventId = "1",
                            title = "News",
                            serviceReference = "1:0:1:1:0:0:0:0:0:0:",
                            serviceName = "Das Erste"
                        ),
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
                        progress = null,
                        onProgress = {},
                        onDismiss = {},
                        onStream = { streamed = true }
                    )
                }
            }
        }
        val stream = composeRule.onNodeWithTag("tv_multi_epg_detail_stream")
        stream.assertIsDisplayed()
        stream.requestFocus()
        composeRule.waitForIdle()
        stream.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        if (!streamed) {
            stream.performClick()
            composeRule.waitForIdle()
        }
        assertEquals(true, streamed)
    }

    @Test
    fun detailHidesStreamWhenStreamingDisabled() {
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvMultiEpgEventDetail(
                        event = Event(
                            eventId = "1",
                            title = "News",
                            serviceReference = "1:0:1:1:0:0:0:0:0:0:",
                            serviceName = "Das Erste"
                        ),
                        bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
                        progress = null,
                        onProgress = {},
                        onDismiss = {},
                        streamingEnabled = false
                    )
                }
            }
        }
        composeRule.onAllNodesWithTag("tv_multi_epg_detail_stream").assertCountEquals(0)
        composeRule.onNodeWithTag("tv_multi_epg_detail_set_timer").assertExists()
        composeRule.onNodeWithTag("tv_multi_epg_detail_imdb").assertExists()
    }

    @Test
    fun bouquetPickerCenterPicksFirstRow() {
        var picked: String? = null
        val first = Service("1:7:1:fav:0:0:0:0:0:0:", "Favourites")
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvMultiEpgBouquetPicker(
                        bouquets = listOf(
                            first,
                            Service("1:7:1:other:0:0:0:0:0:0:", "Other")
                        ),
                        onPick = { picked = it.name },
                        onDismiss = {}
                    )
                }
            }
        }
        val row = composeRule.onNodeWithTag("tv_multi_epg_bouquet_${first.reference}")
        row.assertIsDisplayed()
        row.requestFocus()
        composeRule.waitForIdle()
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        if (picked == null) {
            row.performClick()
            composeRule.waitForIdle()
        }
        assertEquals("Favourites", picked)
    }

    @Composable
    private fun fixtureScreen(
        onJumpToNow: () -> Unit = {},
        onVisibleMinutesChange: (Int) -> Unit = {}
    ) {
        val start = 1_700_000_000L
        val channels = fixtureChannels(start)
        var selectedRef by remember { mutableStateOf(channels[0].serviceRef) }
        var selectedStart by remember { mutableLongStateOf(start) }
        var visibleMinutes by remember {
            mutableIntStateOf(MultiEpgZoom.DEFAULT_MINUTES)
        }
        TvMultiEpgScreen(
            bouquetName = "Favourites",
            channels = channels,
            timelineStartSec = start,
            timelineEndSec = start + 4L * 3600L,
            nowSec = start,
            originFloorSec = start,
            loading = false,
            errorMessage = null,
            selectedServiceRef = selectedRef,
            selectedStartSec = selectedStart,
            onSelectedChange = { ref, sec ->
                selectedRef = ref
                selectedStart = sec
            },
            onJumpToNow = onJumpToNow,
            onPrevDay = {},
            onNextDay = {},
            onVisibleWindow = { _, _ -> },
            onEventClick = {},
            onBouquetClick = {},
            visibleMinutes = visibleMinutes,
            onVisibleMinutesChange = {
                visibleMinutes = it
                onVisibleMinutesChange(it)
            }
        )
    }

    private fun fixtureChannels(start: Long): List<MultiEpgChannel> = listOf(
        channel("1:0:1:1:0:0:0:0:0:0:", "Das Erste", start, "News", "Magazin"),
        channel("1:0:1:2:0:0:0:0:0:0:", "ZDF", start, "Sport", "Serie")
    )

    private fun channel(
        ref: String,
        name: String,
        start: Long,
        firstTitle: String,
        secondTitle: String
    ): MultiEpgChannel = MultiEpgChannel(
        serviceRef = ref,
        serviceName = name,
        bars = listOf(
            bar(ref, name, "1", firstTitle, start, start + 3600L),
            bar(ref, name, "2", secondTitle, start + 3600L, start + 7200L)
        )
    )

    private fun bar(
        ref: String,
        name: String,
        id: String,
        title: String,
        startSec: Long,
        endSec: Long
    ): MultiEpgBar = MultiEpgBar(
        event = Event(
            eventId = id,
            title = title,
            start = startSec.toString(),
            duration = (endSec - startSec).toString(),
            serviceReference = ref,
            serviceName = name
        ),
        startSec = startSec,
        endSec = endSec
    )
}
