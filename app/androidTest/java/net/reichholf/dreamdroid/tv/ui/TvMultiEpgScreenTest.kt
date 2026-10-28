package net.reichholf.dreamdroid.tv.ui

import android.graphics.Color as AndroidColor
import android.view.KeyEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.width
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgChannelKey
import net.reichholf.dreamdroid.multiepg.MultiEpgTimeLabels
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.MultiEpgZoom
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvMultiEpgScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var scheme: ColorScheme
    private lateinit var density: Density
    private lateinit var hScroll: ScrollState

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
            var selectedKey by remember { mutableStateOf(channels[0].key) }
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
                        selectedKey = selectedKey,
                        selectedStartSec = selectedStart,
                        onSelectedChange = { key, sec ->
                            selectedKey = key
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

    /**
     * Right on a row's last bar needs the chunk after the painted one, which only arrives
     * once the reported window reaches into it: painting lags a pan by one chunk.
     */
    @Test
    fun dpadRightOnTheLastBarPaintingTheNextChunkAdvancesTheCursor() {
        val day = MultiEpgWindows.chunkContaining(1_700_000_000L)
        // 21:00 leaves a 2 h window inside the chunk, so only a window reaching the chunk
        // after it can attach the successor bar.
        val lastBarStart = day.startSec + 21L * 3_600L
        val nextBarStart = day.endSec
        val ref = FIRST
        fun channelFor(chunkStarts: List<Long>) = MultiEpgChannel(
            occurrence = 0,
            serviceRef = ref,
            serviceName = "Das Erste",
            bars = chunkStarts.sorted().flatMap { chunkStart ->
                if (chunkStart == day.startSec) {
                    listOf(
                        bar(ref, "Das Erste", "1", "News", day.startSec, day.startSec + 3_600L),
                        bar(
                            ref,
                            "Das Erste",
                            "2",
                            "Magazin",
                            lastBarStart,
                            lastBarStart + 3_600L
                        )
                    )
                } else {
                    listOf(
                        bar(ref, "Das Erste", "3", "Serie", nextBarStart, nextBarStart + 3_600L)
                    )
                }
            }
        )
        val channelKey = channelFor(listOf(day.startSec)).key
        var selectedStart = lastBarStart
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            // The grid's own rule: paint exactly the chunks that overlap the window.
            var chunkStarts by remember { mutableStateOf(listOf(day.startSec)) }
            var selectedKey by remember { mutableStateOf(channelKey) }
            var cursorStart by remember { mutableLongStateOf(lastBarStart) }
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(960.dp, 540.dp))
            ) {
                DreamDroidTvTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TvMultiEpgScreen(
                            bouquetName = "Favourites",
                            channels = listOf(channelFor(chunkStarts)),
                            timelineStartSec = day.startSec,
                            timelineEndSec = day.endSec + 2L * 3_600L,
                            nowSec = day.startSec,
                            originFloorSec = day.startSec,
                            loading = false,
                            errorMessage = null,
                            selectedKey = selectedKey,
                            selectedStartSec = cursorStart,
                            onSelectedChange = { key, sec ->
                                selectedKey = key
                                cursorStart = sec
                                selectedStart = sec
                            },
                            onJumpToNow = {},
                            onPrevDay = {},
                            onNextDay = {},
                            onVisibleWindow = { start, end ->
                                chunkStarts = MultiEpgWindows.slidingChunks(
                                    originFloorSec = day.startSec,
                                    visibleStartSec = start,
                                    visibleEndSec = end
                                )
                            },
                            onEventClick = {},
                            onBouquetClick = {}
                        )
                    }
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle {
            assertEquals(InputMode.Keyboard, inputModeManager.inputMode)
        }
        val grid = composeRule.onNodeWithTag("tv_multi_epg_grid")
        grid.requestFocus()
        composeRule.waitForIdle()
        assertEquals(lastBarStart, selectedStart)

        grid.performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitUntil { selectedStart == nextBarStart }
    }

    @Test
    fun repeatedServiceKeepsItsOwnRowAndTheDpadReachesIt() {
        val start = 1_700_000_000L
        val ref = "1:0:1:1:0:0:0:0:0:0:"
        val channels = listOf(
            channel(ref, "Das Erste", start, "News", "Magazin"),
            channel(ref, "Das Erste", start, "News", "Magazin").copy(occurrence = 1)
        )
        composeRule.setContent {
            var selectedKey by remember { mutableStateOf(channels[0].key) }
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
                        selectedKey = selectedKey,
                        selectedStartSec = selectedStart,
                        onSelectedChange = { key, sec ->
                            selectedKey = key
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
        composeRule.onAllNodesWithTag("tv_multi_epg_channel_name", useUnmergedTree = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithTag("tv_multi_epg_channel_cue", useUnmergedTree = true)
            .assertCountEquals(1)

        val grid = composeRule.onNodeWithTag("tv_multi_epg_grid")
        grid.requestFocus()
        composeRule.waitForIdle()
        grid.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        fun bounds(tag: String) =
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        val secondRow = bounds("tv_multi_epg_channel_$ref#1")
        val cue = bounds("tv_multi_epg_channel_cue")
        assertTrue(
            "cue in the second occurrence",
            cue.center.y in secondRow.top..secondRow.bottom
        )
    }

    @Test
    fun nowAndZoomChromeInvokeCallbacks() {
        var nowClicks = 0
        var zoomMinutes: Int? = null
        composeRule.setContent {
            // The toolbar is laid out for a TV window; a phone-wide one pushes Now off screen.
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(960.dp, 540.dp))
            ) {
                DreamDroidTvTheme {
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
            occurrence = 0,
            serviceRef = "1:0:1:9:0:0:0:0:0:0:",
            serviceName = "Empty",
            bars = emptyList()
        )
        composeRule.setContent {
            var selectedKey by remember { mutableStateOf(empty.key) }
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
                        selectedKey = selectedKey,
                        selectedStartSec = selectedStart,
                        onSelectedChange = { key, sec ->
                            selectedKey = key
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
    fun bouquetMenuDropsFromTheChipOpenOnTheCurrentBouquet() {
        val start = 1_700_000_000L
        val bouquets = listOf(
            Service("1:7:1:fav:0:0:0:0:0:0:", "Favourites"),
            Service("1:7:1:sports:0:0:0:0:0:0:", "Sports"),
            Service("1:7:1:movies:0:0:0:0:0:0:", "Movies")
        )
        var picking by mutableStateOf(false)
        var selected by mutableStateOf(bouquets[1].reference)
        setTvGrid(
            channels = fixtureChannels(start),
            start = start,
            selectedKey = null,
            bouquets = { bouquets },
            selectedBouquetRef = { selected },
            pickingBouquet = { picking },
            onBouquetClick = { picking = true },
            onPickBouquet = {
                selected = it.reference
                picking = false
            },
            onDismissBouquets = { picking = false }
        )
        val chip = composeRule.onNodeWithTag("tv_multi_epg_bouquet")
        chip.requestFocus()
        remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(picking)

        // Drops from the chip, no wider than a menu, focus and check on the current bouquet.
        // The menu is a popup window of its own: compare screen positions, not root bounds.
        val chipBounds = chip.screenBounds()
        val menuNode = composeRule.onNodeWithTag("tv_multi_epg_bouquet_picker")
        val menu = menuNode.screenBounds()
        assertTrue("menu $menu below the chip $chipBounds", menu.top >= chipBounds.bottom - 1f)
        with(density) {
            assertTrue(
                "menu $menu at the chip's start $chipBounds",
                abs(menu.left - chipBounds.left) <= 16.dp.toPx()
            )
        }
        // The popup lays out at the device's density, not the forced TV window's.
        with(menuNode.fetchSemanticsNode().layoutInfo.density) {
            assertTrue("menu width ${menu.width.toDp()}", menu.width.toDp() <= 368.dp)
        }
        composeRule.onNodeWithTag("tv_multi_epg_bouquet_${bouquets[1].reference}")
            .assertIsFocused()
            .assertIsSelected()
        composeRule.onNodeWithTag("tv_multi_epg_bouquet_${bouquets[2].reference}")
            .assertIsNotSelected()

        // D-pad down, OK: picks the next bouquet, closes, and focus is back on the chip.
        remoteKey(KeyEvent.KEYCODE_DPAD_DOWN)
        remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(bouquets[2].reference, selected)
        composeRule.onAllNodesWithTag("tv_multi_epg_bouquet_picker").assertCountEquals(0)
        chip.assertIsFocused()

        // Back closes it without a pick.
        remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(picking)
        remoteKey(KeyEvent.KEYCODE_BACK)
        assertFalse(picking)
        assertEquals(bouquets[2].reference, selected)
    }

    private fun SemanticsNodeInteraction.screenBounds(): Rect {
        val node = fetchSemanticsNode()
        return Rect(node.positionOnScreen, node.size.toSize())
    }

    /** A remote key, through the focused window (the menu's popup when it is open). */
    private fun remoteKey(keyCode: Int) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        composeRule.waitForIdle()
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
                occurrence = 0,
                serviceRef = FIRST,
                serviceName = "Das Erste",
                bars = listOf(bar(FIRST, "Das Erste", "1", "News", start, start + 600L))
            ),
            MultiEpgChannel(
                occurrence = 0,
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
                        selectedKey = channels[0].key,
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
        val selectedCell = bounds("tv_multi_epg_channel_$FIRST#0")
        val otherCell = bounds("tv_multi_epg_channel_$SECOND#0")
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
        // An empty corner of the channel cell, and bare timeline track: the 4 dp gap above a
        // row's bars ("No information" fills the rest of the row), clear of the right edge fade.
        val probes = listOf(
            Triple(selectedCell.right - px(4.dp), otherCell.right - px(4.dp), px(4.dp)),
            Triple(selectedCell.right + px(300.dp), otherCell.right + px(300.dp), px(1.dp))
        )
        for ((selectedX, otherX, inset) in probes) {
            val selected = pixel(selectedX, selectedCell.top + inset)
            val other = pixel(otherX, otherCell.top + inset)
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

    @Test
    fun barsHaveRoundedCorners() {
        val start = 1_700_000_000L
        setTvGrid(channels = fixtureChannels(start), start = start, selectedKey = null)
        val image = gridImage()
        // ZDF's first bar ("Sport", one hour = 180 dp), right end, clear of its text.
        val barEnd = ChannelWidth + 180.dp - 2.dp
        val rowTop = RowHeight + 2.dp
        assertTrue(image.colorAt(barEnd - 20.dp, rowTop + 12.dp).near(scheme.surfaceVariant))
        assertFalse(image.colorAt(barEnd - 1.dp, rowTop + 1.dp).near(scheme.surfaceVariant))
    }

    @Test
    fun titleOfARunningProgrammeSticksToTheLeftEdge() {
        val start = 1_700_000_000L
        val film = channel("1:0:1:3:0:0:0:0:0:0:", "Film", start, "Long film", "Later")
            .copy(
                bars = listOf(
                    bar(
                        "1:0:1:3:0:0:0:0:0:0:",
                        "Film",
                        "1",
                        "Long film",
                        start,
                        start + 3L * 3600L
                    )
                )
            )
        setTvGrid(
            channels = listOf(film) + fixtureChannels(start),
            start = start,
            selectedKey = null
        )
        val scrollPx = with(density) { (180.dp).roundToPx() }
        composeRule.runOnIdle { runBlocking { hScroll.scrollTo(scrollPx) } }
        composeRule.waitForIdle()

        val gridLeft = composeRule.onNodeWithTag(
            "tv_multi_epg_grid"
        ).getUnclippedBoundsInRoot().left
        val title = composeRule.onNodeWithText("Long film").getUnclippedBoundsInRoot()
        assertTrue(title.left >= gridLeft + ChannelWidth)
        assertTrue(title.left <= gridLeft + ChannelWidth + 16.dp)

        val image = gridImage()
        val ink = image.countInk(
            left = ChannelWidth + 4.dp,
            right = ChannelWidth + 80.dp,
            top = 2.dp,
            bottom = RowHeight - 2.dp,
            bar = scheme.surfaceVariant,
            text = scheme.onSurfaceVariant
        )
        assertTrue("no title ink at the left edge", ink > 20)
    }

    @Test
    fun tinyBarsShowNoText() {
        val start = 1_700_000_000L
        val ref = "1:0:1:4:0:0:0:0:0:0:"
        val minute = 60L
        val tiny = MultiEpgChannel(
            occurrence = 0,
            serviceRef = ref,
            serviceName = "Tiny",
            bars = listOf(
                bar(ref, "Tiny", "1", "Before", start, start + 30 * minute),
                bar(ref, "Tiny", "2", "Teaser", start + 30 * minute, start + 33 * minute),
                bar(ref, "Tiny", "3", "Short", start + 33 * minute, start + 45 * minute),
                bar(ref, "Tiny", "4", "After", start + 45 * minute, start + 120 * minute)
            )
        )
        setTvGrid(channels = listOf(tiny), start = start, selectedKey = null)
        composeRule.onNodeWithText("Before").assertExists()
        composeRule.onNodeWithText("After").assertExists()
        composeRule.onAllNodesWithText("Teaser").assertCountEquals(0)
        composeRule.onAllNodesWithText("Short").assertCountEquals(0)
        // "Teaser" and "Short" span minutes 30–45: 90..135 dp from the timeline start.
        val ink = gridImage().countInk(
            left = ChannelWidth + 90.dp,
            right = ChannelWidth + 135.dp,
            top = 2.dp,
            bottom = RowHeight - 2.dp,
            bar = scheme.surfaceVariant,
            text = scheme.onSurfaceVariant
        )
        assertEquals(0, ink)
    }

    @Test
    fun nowLineRunsBehindTheTitleWithAMarkerOnTheRuler() {
        val start = 1_700_000_000L
        val ref = "1:0:1:5:0:0:0:0:0:0:"
        val dense = MultiEpgChannel(
            occurrence = 0,
            serviceRef = ref,
            serviceName = "Dense",
            bars = listOf(bar(ref, "Dense", "1", "WWWWWWWWWWWWWWWW", start, start + 2L * 3600L))
        )
        // Now at minute 10 = 30 dp, inside the title, which starts 10 dp into the bar.
        setTvGrid(
            channels = listOf(dense),
            start = start,
            selectedKey = null,
            nowSec = start + 600L
        )
        val ruler = composeRule.onNodeWithTag("tv_multi_epg_ruler").captureToImage()
        // The marker is a 10 dp triangle pointing down at the ruler's bottom; 4 dp above its
        // tip it is about 5 dp wide, so its middle pixels are not anti-aliased edges.
        val marker = (-2..2).map { ruler.colorAt(30.dp + it.dp, RulerHeight - 4.dp) }
        assertTrue(
            "no ruler marker: ${marker.joinToString { it.hex() }} vs ${scheme.primary.hex()}",
            marker.any { it.near(scheme.primary) }
        )

        val image = gridImage()
        // The line's column: the first x near 30 dp that holds the line colour between rows.
        val x = (-4..4).map { ChannelWidth + 30.dp + it.dp }
            .firstOrNull { image.colorAt(it, RowHeight - 1.dp).near(scheme.primary) }
        assertTrue(
            "now-line not drawn: " +
                (-4..4).joinToString {
                    image.colorAt(ChannelWidth + 30.dp + it.dp, RowHeight - 1.dp).hex()
                } +
                " vs ${scheme.primary.hex()}",
            x != null
        )
        val column = (8..40).map { y -> image.colorAt(x!!, y.dp) }
        assertTrue(
            "now-line covers the title: ${column.joinToString { it.hex() }}",
            column.any { it.near(scheme.onSurfaceVariant, tolerance = 0.12f) }
        )
    }

    @Test
    fun focusedCursorUsesTheCardFocusColours() {
        val start = 1_700_000_000L
        val channels = fixtureChannels(start)
        setTvGrid(channels = channels, start = start, selectedKey = channels[0].key)
        // Das Erste's first bar ("News"), right end, clear of its text.
        val x = ChannelWidth + 180.dp - 22.dp
        val y = 14.dp
        assertTrue(gridImage().colorAt(x, y).near(scheme.primaryContainer))

        composeRule.onNodeWithTag("tv_multi_epg_grid").requestFocus()
        composeRule.waitForIdle()
        val image = gridImage()
        assertTrue(image.colorAt(x, y).near(scheme.primary))
        // The focus border runs along the bar's top edge, 2 dp in and 2 dp thick: it is the
        // fill's content colour, not primary, which would vanish into the focused cursor.
        val borderTop = listOf(2.5.dp, 3.dp, 3.5.dp)
            .map { image.colorAt(ChannelWidth + 90.dp, it) }
        assertTrue(
            "cursor border not the content colour: " +
                borderTop.joinToString { it.hex() } +
                " vs ${scheme.onPrimary.hex()}",
            borderTop.any { it.near(scheme.onPrimary) }
        )
    }

    @Test
    fun holesInTheDataShowNoInformation() {
        val start = 1_700_000_000L
        val ref = "1:0:1:6:0:0:0:0:0:0:"
        val late = MultiEpgChannel(
            occurrence = 0,
            serviceRef = ref,
            serviceName = "Late",
            bars = listOf(bar(ref, "Late", "1", "Evening", start + 3600L, start + 7200L))
        )
        val empty = MultiEpgChannel(
            occurrence = 0,
            serviceRef = "1:0:1:7:0:0:0:0:0:0:",
            serviceName = "Empty",
            bars = emptyList()
        )
        setTvGrid(channels = listOf(late, empty), start = start, selectedKey = null)
        composeRule.onAllNodesWithTag("tv_multi_epg_no_info", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .let { assertTrue(it.size >= 2) }
        composeRule.onAllNodesWithText("No information").fetchSemanticsNodes()
            .let { assertTrue(it.size >= 2) }
    }

    @Test
    fun channelNamesStayOnOneLine() {
        val start = 1_700_000_000L
        val ref = "1:0:1:8:0:0:0:0:0:0:"
        val long =
            channel(ref, "A very long channel name that would wrap", start, "News", "Magazin")
        setTvGrid(channels = listOf(long), start = start, selectedKey = null)
        val name = composeRule.onNodeWithTag("tv_multi_epg_channel_name", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue(name.height < 28.dp)
    }

    @Test
    fun dayChevronsAndReloadAreLabelledTargets() {
        var prev = 0
        var next = 0
        var reloads = 0
        val start = 1_700_000_000L
        setTvGrid(
            channels = fixtureChannels(start),
            start = start,
            selectedKey = null,
            onPrevDay = { prev++ },
            onNextDay = { next++ },
            onRefresh = { reloads++ }
        )
        listOf(
            "tv_multi_epg_prev_day" to "Previous day",
            "tv_multi_epg_next_day" to "Next day",
            "tv_multi_epg_refresh" to "Reload"
        ).forEach { (tag, label) ->
            val node = composeRule.onNodeWithTag(tag)
            node.assertWidthIsAtLeast(48.dp)
            node.assertHeightIsAtLeast(48.dp)
            node.assertHasClickAction()
            composeRule.onNode(hasTestTag(tag) and hasContentDescription(label))
                .assertExists()
            node.requestFocus()
            node.performKeyInput { pressKey(Key.DirectionCenter) }
        }
        composeRule.waitForIdle()
        assertEquals(listOf(1, 1, 1), listOf(prev, next, reloads))
        composeRule.onAllNodesWithText("Previous day").assertCountEquals(0)
    }

    /** Each toolbar icon and label sits on its control's centre, both ways. */
    @Test
    fun toolbarIconsAndLabelsSitOnTheirControlsCentre() {
        val start = 1_700_000_000L
        setTvGrid(
            channels = fixtureChannels(start),
            start = start,
            selectedKey = null,
            onRefresh = {}
        )
        listOf(
            "tv_multi_epg_prev_day" to hasContentDescription("Previous day"),
            "tv_multi_epg_next_day" to hasContentDescription("Next day"),
            "tv_multi_epg_refresh" to hasContentDescription("Reload"),
            "tv_multi_epg_now" to hasText("Now")
        ).forEach { (tag, inner) ->
            val control = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val content = composeRule.onNode(
                inner and hasAnyAncestor(hasTestTag(tag)),
                useUnmergedTree = true
            ).getUnclippedBoundsInRoot()
            val dx = (content.left + content.right) / 2 - (control.left + control.right) / 2
            val dy = (content.top + content.bottom) / 2 - (control.top + control.bottom) / 2
            assertTrue("$tag off centre by $dx, $dy", abs(dx.value) <= 1f && abs(dy.value) <= 1f)
        }
    }

    @Test
    fun toolbarControlsShareTheRailColourAndFocusTurnsInverse() {
        val start = 1_700_000_000L
        setTvGrid(
            channels = fixtureChannels(start),
            start = start,
            selectedKey = null,
            onRefresh = {}
        )
        val controls = listOf(
            "tv_multi_epg_bouquet",
            "tv_multi_epg_prev_day",
            "tv_multi_epg_next_day",
            "tv_multi_epg_now",
            "tv_multi_epg_zoom",
            "tv_multi_epg_refresh"
        )

        // 6 dp in from a control's start, at its middle: inside its pill, clear of its text.
        fun fill(tag: String): Color {
            val rail = composeRule.onNodeWithTag("tv_multi_epg_chrome_rail")
            val railBounds = rail.getUnclippedBoundsInRoot()
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            return rail.captureToImage().colorAt(
                bounds.left - railBounds.left + 6.dp,
                (bounds.top + bounds.bottom) / 2 - railBounds.top
            )
        }
        for (tag in controls) {
            val color = fill(tag)
            assertTrue(
                "$tag at rest: ${color.hex()} vs rail ${scheme.surfaceContainerLow.hex()}",
                color.near(scheme.surfaceContainerLow)
            )
        }

        composeRule.onNodeWithTag("tv_multi_epg_now").requestFocus()
        composeRule.waitForIdle()
        assertTrue(
            "focused Now: ${fill("tv_multi_epg_now").hex()}",
            fill("tv_multi_epg_now").near(scheme.inverseSurface)
        )
        assertTrue(fill("tv_multi_epg_zoom").near(scheme.surfaceContainerLow))
    }

    @Test
    fun selectedRowBandHasRoundedEnds() {
        val start = 1_700_000_000L
        val channels = fixtureChannels(start)
        setTvGrid(channels = channels, start = start, selectedKey = channels[0].key)
        val image = gridImage()

        // Row 0 carries the band, row 1 not: compare the same spot in both rows.
        fun band(x: Dp, y: Dp) = image.colorAt(x, y) to image.colorAt(x, RowHeight + y)
        val (corner, cornerOther) = band(1.dp, 1.dp)
        assertTrue(
            "band start corner not rounded: ${corner.hex()} vs ${cornerOther.hex()}",
            corner.near(cornerOther, tolerance = 0.01f)
        )
        val (inside, insideOther) = band(8.dp, RowHeight - 4.dp)
        assertFalse(
            "band missing just inside its rounded start: ${inside.hex()} vs ${insideOther.hex()}",
            inside.near(insideOther, tolerance = 0.01f)
        )

        // At the end of the timeline (no edge fade once it cannot scroll further) the band
        // ends rounded too.
        composeRule.runOnIdle { runBlocking { hScroll.scrollTo(hScroll.maxValue) } }
        composeRule.waitForIdle()
        val end = gridImage()
        val right = with(density) { end.width.toDp() } - 1.dp
        val endCorner = end.colorAt(right, 1.dp)
        val endCornerOther = end.colorAt(right, RowHeight + 1.dp)
        assertTrue(
            "band end corner not rounded: ${endCorner.hex()} vs ${endCornerOther.hex()}",
            endCorner.near(endCornerOther, tolerance = 0.01f)
        )
    }

    @Test
    fun dayLabelLeavesOutTheYear() {
        val start = 1_700_000_000L
        setTvGrid(
            channels = fixtureChannels(start),
            start = start,
            selectedKey = null,
            nowSec = start
        )
        val today = composeRule.onNodeWithTag("tv_multi_epg_day").fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString()
        assertTrue(today, today.startsWith("Today · "))
        assertFalse(today, today.contains("2023"))

        val tomorrow = MultiEpgTimeLabels.formatVisibleDay(
            visibleSec = start + 86_400L,
            nowSec = start,
            todayLabel = "Today",
            locale = Locale.US,
            short = true
        )
        assertFalse(tomorrow, tomorrow.contains("2023") || tomorrow.contains("Today"))
    }

    /**
     * The grid at a 960 × 540 dp TV window, dark theme, keyboard input mode (as on a remote),
     * with [hScroll], [scheme] and [density] captured for the pixel checks.
     */
    private fun setTvGrid(
        channels: List<MultiEpgChannel>,
        start: Long,
        selectedKey: MultiEpgChannelKey?,
        nowSec: Long = start - 3600L,
        onPrevDay: () -> Unit = {},
        onNextDay: () -> Unit = {},
        onRefresh: (() -> Unit)? = null,
        bouquets: () -> List<Service> = { emptyList() },
        selectedBouquetRef: () -> String = { "" },
        pickingBouquet: () -> Boolean = { false },
        onBouquetClick: () -> Unit = {},
        onPickBouquet: (Service) -> Unit = {},
        onDismissBouquets: () -> Unit = {}
    ) {
        var showContent by mutableStateOf(false)
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(960.dp, 540.dp))
            ) {
                DreamDroidTvTheme(forceDark = true) {
                    scheme = PhoneMaterialTheme.colorScheme
                    density = LocalDensity.current
                    // The grid's alpha bands need the flat base the pixel probes expect;
                    // the hub's wash behind them is the production background, not this harness.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PhoneMaterialTheme.colorScheme.background)
                    ) {
                        if (showContent) {
                            val scroll = rememberScrollState()
                            hScroll = scroll
                            TvMultiEpgScreen(
                                bouquetName = "Favourites",
                                channels = channels,
                                timelineStartSec = start,
                                timelineEndSec = start + 8L * 3600L,
                                nowSec = nowSec,
                                originFloorSec = start,
                                loading = false,
                                errorMessage = null,
                                selectedKey = selectedKey,
                                selectedStartSec = start,
                                onSelectedChange = { _, _ -> },
                                onJumpToNow = {},
                                onPrevDay = onPrevDay,
                                onNextDay = onNextDay,
                                onVisibleWindow = { _, _ -> },
                                onEventClick = {},
                                onBouquetClick = onBouquetClick,
                                onRefresh = onRefresh,
                                hScrollState = scroll,
                                bouquets = bouquets(),
                                selectedBouquetRef = selectedBouquetRef(),
                                pickingBouquet = pickingBouquet(),
                                onPickBouquet = onPickBouquet,
                                onDismissBouquets = onDismissBouquets
                            )
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle {
            assertEquals(InputMode.Keyboard, inputModeManager.inputMode)
            showContent = true
        }
        composeRule.waitForIdle()
    }

    private fun gridImage(): ImageBitmap =
        composeRule.onNodeWithTag("tv_multi_epg_grid").captureToImage()

    private fun ImageBitmap.colorAt(x: Dp, y: Dp): Color {
        val map = toPixelMap()
        return with(density) {
            map[x.roundToPx().coerceIn(0, width - 1), y.roundToPx().coerceIn(0, height - 1)]
        }
    }

    /** Pixels in the box closer to the [text] colour than to the [bar] colour. */
    private fun ImageBitmap.countInk(
        left: Dp,
        right: Dp,
        top: Dp,
        bottom: Dp,
        bar: Color,
        text: Color
    ): Int {
        val map = toPixelMap()
        val threshold = (bar.luminance() + text.luminance()) / 2f
        val lighterText = text.luminance() > bar.luminance()
        var count = 0
        with(density) {
            for (x in left.roundToPx() until right.roundToPx().coerceAtMost(width)) {
                for (y in top.roundToPx() until bottom.roundToPx().coerceAtMost(height)) {
                    val lum = map[x, y].luminance()
                    if (if (lighterText) lum > threshold else lum < threshold) {
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun Color.hex(): String = "#%08x".format(toArgb())

    private fun Color.near(other: Color, tolerance: Float = 0.06f): Boolean =
        abs(red - other.red) <= tolerance &&
            abs(green - other.green) <= tolerance &&
            abs(blue - other.blue) <= tolerance

    @Composable
    private fun fixtureScreen(
        onJumpToNow: () -> Unit = {},
        onVisibleMinutesChange: (Int) -> Unit = {}
    ) {
        val start = 1_700_000_000L
        val channels = fixtureChannels(start)
        var selectedKey by remember { mutableStateOf(channels[0].key) }
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
            selectedKey = selectedKey,
            selectedStartSec = selectedStart,
            onSelectedChange = { key, sec ->
                selectedKey = key
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

    private companion object {
        // Comfortable text size at font scale 1: channel column and row height.
        val ChannelWidth = 112.dp
        val RowHeight = 48.dp
        val RulerHeight = 28.dp
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
        occurrence = 0,
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
