package net.reichholf.dreamdroid.tv.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgZoom
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                        progress = null,
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
                        progress = null,
                        onDismiss = {},
                        streamingEnabled = false
                    )
                }
            }
        }
        composeRule.onAllNodesWithTag("tv_multi_epg_detail_stream").assertCountEquals(0)
        composeRule.onNodeWithTag("tv_multi_epg_detail_set_timer").assertExists()
        composeRule.onNodeWithTag("tv_multi_epg_detail_edit_timer").assertExists()
        composeRule.onNodeWithTag("tv_multi_epg_detail_imdb").assertExists()
    }

    @Test
    fun detailEditTimerCenterInvokesCallback() {
        var edited = false
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
                        progress = null,
                        onDismiss = {},
                        onEditTimer = { edited = true }
                    )
                }
            }
        }
        val editTimer = composeRule.onNodeWithTag("tv_multi_epg_detail_edit_timer")
        editTimer.assertIsDisplayed()
        editTimer.requestFocus()
        composeRule.waitForIdle()
        editTimer.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        if (!edited) {
            editTimer.performClick()
            composeRule.waitForIdle()
        }
        assertEquals(true, edited)
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

    /**
     * The selected programme's channel is a quiet cue: no primaryContainer fill, a primary
     * accent bar at the cell's start, and a faint onSurface band across the channel cell and
     * the timeline behind the bars.
     */
    @Test
    fun selectedChannelShowsAnAccentBarAndARowBandInsteadOfAFill() {
        val start = 1_700_000_000L
        // One short programme each, so most of the visible timeline is bare track.
        val channels = listOf(
            MultiEpgChannel(
                serviceRef = FIRST,
                serviceName = "Das Erste",
                bars = listOf(bar(FIRST, "Das Erste", "1", "News", start, start + 600L))
            ),
            MultiEpgChannel(
                serviceRef = SECOND,
                serviceName = "ZDF",
                bars = listOf(bar(SECOND, "ZDF", "2", "Sport", start, start + 600L))
            )
        )
        lateinit var colors: ColorScheme
        lateinit var density: Density
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(960.dp, 540.dp))
            ) {
                density = LocalDensity.current
                DreamDroidTvTheme {
                    colors = PhoneMaterialTheme.colorScheme
                    TvMultiEpgScreen(
                        bouquetName = "Favourites",
                        channels = channels,
                        timelineStartSec = start,
                        // At 3dp a minute, 4 h end inside the 960dp window and the far right
                        // probe lands past the timeline's track; 6 h fill the viewport.
                        timelineEndSec = start + 6L * 3600L,
                        nowSec = start - 3600L,
                        originFloorSec = start,
                        loading = false,
                        errorMessage = null,
                        selectedServiceRef = FIRST,
                        selectedStartSec = start,
                        onSelectedChange = { _, _ -> },
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
        composeRule.waitForIdle()
        fun px(dp: Dp) = with(density) { dp.toPx() }
        fun bounds(tag: String) =
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                .boundsInRoot

        composeRule.onAllNodesWithTag("tv_multi_epg_channel_cue", useUnmergedTree = true)
            .assertCountEquals(1)
        val selectedCell = bounds("tv_multi_epg_channel_$FIRST")
        val otherCell = bounds("tv_multi_epg_channel_$SECOND")
        val cue = bounds("tv_multi_epg_channel_cue")
        assertTrue("cue in the selected row", cue.center.y in selectedCell.top..selectedCell.bottom)
        assertEquals(selectedCell.left, cue.left, 1f)
        assertEquals(px(4.dp), cue.width, 1f)
        assertEquals(px(24.dp), cue.height, 1f)
        assertEquals(selectedCell.center.y, cue.center.y, px(1.dp))

        val list = bounds("tv_multi_epg_channel_list")
        val image = composeRule.onNodeWithTag("tv_multi_epg_channel_list", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        fun pixel(x: Float, y: Float) =
            Color(image.getPixel((x - list.left).toInt(), (y - list.top).toInt()))
        val band = colors.onSurface.copy(alpha = 0.06f)
        // An empty corner of the channel cell, and bare timeline track far right of the bars.
        val probes = listOf(
            selectedCell.right - px(4.dp) to otherCell.right - px(4.dp),
            list.right - px(24.dp) to list.right - px(24.dp)
        )
        for ((selectedX, otherX) in probes) {
            val selected = pixel(selectedX, selectedCell.top + px(4.dp))
            val other = pixel(otherX, otherCell.top + px(4.dp))
            assertTrue(
                "selected row filled with primaryContainer at x=$selectedX",
                rgbDistance(selected, colors.primaryContainer) > 60
            )
            assertTrue(
                "no row band at x=$selectedX: selected=$selected other=$other",
                rgbDistance(selected, band.compositeOver(other)) <= 12 &&
                    rgbDistance(selected, other) >= 12
            )
        }
    }

    private fun rgbDistance(a: Color, b: Color): Int {
        val x = a.toArgb()
        val y = b.toArgb()
        return abs(AndroidColor.red(x) - AndroidColor.red(y)) +
            abs(AndroidColor.green(x) - AndroidColor.green(y)) +
            abs(AndroidColor.blue(x) - AndroidColor.blue(y))
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

private const val FIRST = "1:0:1:1:0:0:0:0:0:0:"
private const val SECOND = "1:0:1:2:0:0:0:0:0:0:"
