package net.reichholf.dreamdroid.ui.multiepg

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgTimerClock
import net.reichholf.dreamdroid.multiepg.buildMultiEpgChannels
import net.reichholf.dreamdroid.multiepg.multiEpgTimerClockKey
import net.reichholf.dreamdroid.multiepg.playableMultiEpgRoster
import net.reichholf.dreamdroid.ui.compose.PULL_REFRESH_INDICATOR_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MultiEpgScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .commit()
    }

    @Test
    fun buildChannelsGroupsByService() {
        val events = listOf(
            Event(
                eventId = "1",
                title = "News",
                start = "1000",
                duration = "600",
                serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste",
            ),
            Event(
                eventId = "2",
                title = "Sport",
                start = "1000",
                duration = "900",
                serviceReference = "1:0:1:2:1:1:0:0:0:0:",
                serviceName = "ZDF",
            ),
            Event(
                eventId = "3",
                title = "Weather",
                start = "1600",
                duration = "300",
                serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste",
            ),
        )
        val channels = buildMultiEpgChannels(events)
        assertEquals(2, channels.size)
        assertEquals("Das Erste", channels[0].serviceName)
        assertEquals(2, channels[0].bars.size)
        assertEquals("ZDF", channels[1].serviceName)
    }

    @Test
    fun buildChannelsReusesBarsWhenAppendingAnotherDay() {
        val news = Event(
            eventId = "1",
            title = "News",
            start = "1000",
            duration = "600",
            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
            serviceName = "Das Erste",
        )
        val first = buildMultiEpgChannels(listOf(news))
        val film = Event(
            eventId = "2",
            title = "Film",
            start = "90000",
            duration = "3600",
            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
            serviceName = "Das Erste",
        )
        val newsAgain = news.copy()
        val second = buildMultiEpgChannels(listOf(newsAgain, film), first)
        assertEquals(2, second[0].bars.size)
        assertSame(first[0].bars[0], second[0].bars[0])
        assertNotSame(first[0], second[0])
        val dropped = buildMultiEpgChannels(listOf(news.copy()), second)
        assertEquals(1, dropped[0].bars.size)
        assertSame(first[0].bars[0], dropped[0].bars[0])
    }

    @Test
    fun playableRosterSkipsMarkersAndDirectories() {
        val live = Service("1:0:1:1:1:1:0:0:0:0:", "Das Erste")
        val marker = Service("1:64:0:0:0:0:0:0:0:0:", "---")
        val directory = Service(
            "1:7:1:0:0:0:FROM BOUQUET \"bouquets.tv\" ORDER BY bouquet",
            "Favourites",
        )
        val roster = playableMultiEpgRoster(listOf(marker, live, directory))
        assertEquals(1, roster.size)
        assertEquals("Das Erste", roster[0].name)
    }

    @Test
    fun buildChannelsKeepsRosterRowsWithoutEvents() {
        val liveA = Service("1:0:1:1:1:1:0:0:0:0:", "Das Erste")
        val liveB = Service("1:0:1:2:1:1:0:0:0:0:", "ZDF")
        val news = Event(
            eventId = "1",
            title = "News",
            start = "1000",
            duration = "600",
            serviceReference = liveA.reference,
            serviceName = liveA.name,
        )
        val channels = buildMultiEpgChannels(
            events = listOf(news),
            roster = listOf(liveA, liveB),
        )
        assertEquals(2, channels.size)
        assertEquals("Das Erste", channels[0].serviceName)
        assertEquals(1, channels[0].bars.size)
        assertEquals("ZDF", channels[1].serviceName)
        assertEquals(0, channels[1].bars.size)
        val again = buildMultiEpgChannels(
            events = listOf(news.copy()),
            previous = channels,
            roster = listOf(liveA, liveB),
        )
        assertSame(channels[1], again[1])
        assertSame(channels[0].bars[0], again[0].bars[0])
    }

    @Test
    fun buildChannelsReturnsPreviousListWhenUnchanged() {
        val events = listOf(
            Event(
                eventId = "1",
                title = "News",
                start = "1000",
                duration = "600",
                serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste",
            ),
        )
        val first = buildMultiEpgChannels(events)
        val second = buildMultiEpgChannels(listOf(events[0].copy()), first)
        assertSame(first, second)
    }

    @Test
    fun buildChannelsReplacesBarWhenTitleChanges() {
        val first = buildMultiEpgChannels(
            listOf(
                Event(
                    eventId = "1",
                    title = "News",
                    start = "1000",
                    duration = "600",
                    serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                    serviceName = "Das Erste",
                ),
            ),
        )
        val second = buildMultiEpgChannels(
            listOf(
                Event(
                    eventId = "1",
                    title = "News 2",
                    start = "1000",
                    duration = "600",
                    serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                    serviceName = "Das Erste",
                ),
            ),
            first,
        )
        assertNotSame(first[0].bars[0], second[0].bars[0])
        assertEquals("News 2", second[0].bars[0].event.title)
    }

    @Test
    fun gridShowsChannelsAndProgrammeTitles() {
        val start = 1_700_000_000L
        val channels = listOf(
            MultiEpgChannel(
                serviceRef = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "10",
                            title = "Tagesschau",
                            start = start.toString(),
                            duration = "1800",
                            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD",
                        ),
                        startSec = start,
                        endSec = start + 1800,
                    ),
                ),
            ),
            MultiEpgChannel(
                serviceRef = "1:0:1:2:1:1:0:0:0:0:",
                serviceName = "ZDF HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "11",
                            title = "heute",
                            start = start.toString(),
                            duration = "1800",
                            serviceReference = "1:0:1:2:1:1:0:0:0:0:",
                            serviceName = "ZDF HD",
                        ),
                        startSec = start,
                        endSec = start + 1800,
                    ),
                ),
            ),
        )
        var clicked = ""
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = channels,
                    timelineStartSec = start,
                    timelineEndSec = start + 7200,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = { clicked = it.title },
                )
            }
        }

        composeRule.onNodeWithTag("multi_epg_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("ZDF HD").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").performClick()
        composeRule.waitForIdle()
        assertEquals("Tagesschau", clicked)
    }

    @Test
    fun gridKeepsChannelNameWhenRowHasNoBars() {
        val start = 1_700_000_000L
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = listOf(
                        MultiEpgChannel(
                            serviceRef = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD",
                            bars = listOf(
                                MultiEpgBar(
                                    event = Event(
                                        eventId = "10",
                                        title = "Tagesschau",
                                        start = start.toString(),
                                        duration = "1800",
                                        serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                                        serviceName = "Das Erste HD",
                                    ),
                                    startSec = start,
                                    endSec = start + 1800,
                                ),
                            ),
                        ),
                        MultiEpgChannel(
                            serviceRef = "1:0:1:2:1:1:0:0:0:0:",
                            serviceName = "Deluxe Music HD",
                            bars = emptyList(),
                        ),
                    ),
                    timelineStartSec = start,
                    timelineEndSec = start + 7200,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Deluxe Music HD").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
    }

    @Test
    fun gridShowsBarsAfterTimelineArrivesFromUnixEpoch() {
        val start = 1_700_000_000L
        val loaded = listOf(
            MultiEpgChannel(
                serviceRef = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "10",
                            title = "Tagesschau",
                            start = start.toString(),
                            duration = "1800",
                            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD",
                        ),
                        startSec = start,
                        endSec = start + 1800,
                    ),
                ),
            ),
        )
        var timelineStart by mutableLongStateOf(0L)
        var timelineEnd by mutableLongStateOf(0L)
        var channels by mutableStateOf(emptyList<MultiEpgChannel>())
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = channels,
                    timelineStartSec = timelineStart,
                    timelineEndSec = timelineEnd,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                )
            }
        }
        composeRule.runOnIdle {
            timelineStart = start
            timelineEnd = start + 7200
            channels = loaded
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
    }

    @Test
    fun syncIndicatorVisibleWhileLoading() {
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = emptyList(),
                    timelineStartSec = 0L,
                    timelineEndSec = 3600L,
                    nowSec = 60L,
                    loading = true,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                )
            }
        }
        composeRule.onNodeWithTag("multi_epg_sync_indicator").assertIsDisplayed()
    }

    @Test
    fun dayButtonsInvokeCallbacks() {
        var prev = 0
        var next = 0
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = emptyList(),
                    timelineStartSec = 0L,
                    timelineEndSec = 3600L,
                    nowSec = 60L,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onPrevDay = { prev += 1 },
                    onNextDay = { next += 1 },
                    onEventClick = {},
                )
            }
        }
        composeRule.onNodeWithText("−1d").performClick()
        composeRule.onNodeWithText("+1d").performClick()
        composeRule.waitForIdle()
        assertEquals(1, prev)
        assertEquals(1, next)
    }

    @Test
    fun errorBannerKeepsProgrammeBars() {
        val start = 1_700_000_000L
        val channels = listOf(
            MultiEpgChannel(
                serviceRef = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "10",
                            title = "Tagesschau",
                            start = start.toString(),
                            duration = "1800",
                            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD",
                        ),
                        startSec = start,
                        endSec = start + 1800,
                    ),
                ),
            ),
        )
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = channels,
                    timelineStartSec = start,
                    timelineEndSec = start + 7200,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = "box down",
                    onJumpToNow = {},
                    onEventClick = {},
                )
            }
        }
        composeRule.onNodeWithText("box down").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
    }

    @Test
    fun pullRefreshingShowsIndicator() {
        val start = 1_700_000_000L
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = emptyList(),
                    timelineStartSec = start,
                    timelineEndSec = start + 3600,
                    nowSec = start + 60,
                    loading = true,
                    pullRefreshing = true,
                    errorMessage = null,
                    onJumpToNow = {},
                    onRefresh = {},
                    onEventClick = {},
                )
            }
        }
        composeRule.onNodeWithTag("multi_epg_sync_indicator").assertIsDisplayed()
        composeRule.onNodeWithTag(PULL_REFRESH_INDICATOR_TAG).assertIsDisplayed()
    }

    @Test
    fun dayLabelShowsTodayForVisibleWindow() {
        val start = 1_700_000_000L
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = listOf(
                        MultiEpgChannel(
                            serviceRef = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD",
                            bars = listOf(
                                MultiEpgBar(
                                    event = Event(
                                        eventId = "10",
                                        title = "Tagesschau",
                                        start = start.toString(),
                                        duration = "1800",
                                        serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                                        serviceName = "Das Erste HD",
                                    ),
                                    startSec = start,
                                    endSec = start + 1800,
                                ),
                            ),
                        ),
                    ),
                    timelineStartSec = start,
                    timelineEndSec = start + 7200,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                )
            }
        }
        composeRule.onNodeWithTag("multi_epg_day_label").assertIsDisplayed()
        composeRule.onNodeWithText("Today", substring = true).assertIsDisplayed()
    }

    @Test
    fun originJumpKeepsScrolledProgrammeAndDayLabel() {
        val origin = 1_700_000_000L
        val visibleOffsetSec = 21L * 3600L
        val barStart = origin + visibleOffsetSec
        var timelineStart by mutableLongStateOf(origin)
        val channels = listOf(
            MultiEpgChannel(
                serviceRef = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "10",
                            title = "NightShow",
                            start = barStart.toString(),
                            duration = "3600",
                            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD",
                        ),
                        startSec = barStart,
                        endSec = barStart + 3600,
                    ),
                ),
            ),
        )
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = channels,
                    timelineStartSec = timelineStart,
                    timelineEndSec = origin + 3L * 86400L,
                    nowSec = origin + 60,
                    loading = true,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                    focusSec = barStart,
                    focusEpoch = 1,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NightShow").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_epg_sync_indicator").assertIsDisplayed()
        val dayBefore = dayLabelText()

        composeRule.runOnIdle {
            timelineStart = origin + visibleOffsetSec
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NightShow").assertIsDisplayed()
        assertEquals(dayBefore, dayLabelText())
        composeRule.onNodeWithTag("multi_epg_sync_indicator").assertIsDisplayed()
    }

    @Test
    fun recordClockShowsOnMatchingBar() {
        val start = 1_700_000_000L
        val ref = "1:0:1:1:1:1:0:0:0:0:"
        val channels = listOf(
            MultiEpgChannel(
                serviceRef = ref,
                serviceName = "Das Erste HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "10",
                            title = "Tagesschau",
                            start = start.toString(),
                            duration = "1800",
                            serviceReference = ref,
                            serviceName = "Das Erste HD",
                        ),
                        startSec = start,
                        endSec = start + 1800,
                    ),
                ),
            ),
        )
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = channels,
                    timelineStartSec = start,
                    timelineEndSec = start + 7200,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                    timerClocks = mapOf(
                        multiEpgTimerClockKey(ref, "10", start) to
                            MultiEpgTimerClock.Record,
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_epg_timer_record", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun compactRowsAreShorterThanComfortable() {
        var size by mutableStateOf(MultiEpgTextSize.Compact)
        val start = 1_700_000_000L
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = oneChannel(start),
                    timelineStartSec = start,
                    timelineEndSec = start + 7200,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                    textSize = size,
                )
            }
        }
        composeRule.onNodeWithTag("multi_epg_row").assertHeightIsEqualTo(36.dp)
        composeRule.runOnIdle { size = MultiEpgTextSize.Comfortable }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("multi_epg_row").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun compactRowsGrowWithFontScale() {
        val start = 1_700_000_000L
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                DreamDroidTheme {
                    MultiEpgScreen(
                        bouquetName = "Favourites",
                        channels = oneChannel(start),
                        timelineStartSec = start,
                        timelineEndSec = start + 7200,
                        nowSec = start + 60,
                        loading = false,
                        errorMessage = null,
                        onJumpToNow = {},
                        onEventClick = {},
                        textSize = MultiEpgTextSize.Compact,
                    )
                }
            }
        }
        composeRule.onNodeWithTag("multi_epg_row").assertHeightIsEqualTo(54.dp)
    }

    @Test
    fun comfortableClockIsLargerThanCompact() {
        var size by mutableStateOf(MultiEpgTextSize.Compact)
        val start = 1_700_000_000L
        val ref = "1:0:1:1:1:1:0:0:0:0:"
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = oneChannel(start, ref),
                    timelineStartSec = start,
                    timelineEndSec = start + 7200,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                    textSize = size,
                    timerClocks = mapOf(
                        multiEpgTimerClockKey(ref, "10", start) to
                            MultiEpgTimerClock.Record,
                    ),
                )
            }
        }
        composeRule.onNodeWithTag(
            "multi_epg_timer_record",
            useUnmergedTree = true,
        ).assertHeightIsEqualTo(12.dp)
        composeRule.runOnIdle { size = MultiEpgTextSize.Comfortable }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(
            "multi_epg_timer_record",
            useUnmergedTree = true,
        ).assertHeightIsEqualTo(16.dp)
    }

    private fun oneChannel(
        start: Long,
        serviceRef: String = "1:0:1:1:1:1:0:0:0:0:",
    ): List<MultiEpgChannel> {
        return listOf(
            MultiEpgChannel(
                serviceRef = serviceRef,
                serviceName = "Das Erste HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "10",
                            title = "Tagesschau",
                            start = start.toString(),
                            duration = "1800",
                            serviceReference = serviceRef,
                            serviceName = "Das Erste HD",
                        ),
                        startSec = start,
                        endSec = start + 1800,
                    ),
                ),
            ),
        )
    }

    private fun dayLabelText(): String {
        val node = composeRule.onNodeWithTag("multi_epg_day_label").fetchSemanticsNode()
        return node.config[SemanticsProperties.Text].joinToString { it.text }
    }

    @Test
    fun zoomMenuSelectsFiveHourSpan() {
        var visibleMinutes by mutableIntStateOf(MULTI_EPG_VISIBLE_MINUTES)
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = emptyList(),
                    timelineStartSec = 0L,
                    timelineEndSec = 3600L,
                    nowSec = 60L,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                    visibleMinutes = visibleMinutes,
                    onVisibleMinutesChange = { visibleMinutes = it },
                )
            }
        }
        composeRule.onNodeWithTag("multi_epg_zoom").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_epg_zoom").performClick()
        composeRule.onNodeWithText("1h").assertIsDisplayed()
        composeRule.onNodeWithText("4h").assertIsDisplayed()
        composeRule.onNodeWithText("5h").performClick()
        composeRule.waitForIdle()
        assertEquals(300, visibleMinutes)
    }

    @Test
    fun zoomOutWidensVisibleWindowAndKeepsNearBar() {
        val start = 1_700_000_000L
        val lateStart = start + 3L * 3600L
        val channels = listOf(
            MultiEpgChannel(
                serviceRef = "1:0:1:1:1:1:0:0:0:0:",
                serviceName = "Das Erste HD",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "10",
                            title = "Tagesschau",
                            start = start.toString(),
                            duration = "1800",
                            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD",
                        ),
                        startSec = start,
                        endSec = start + 1800,
                    ),
                    MultiEpgBar(
                        event = Event(
                            eventId = "11",
                            title = "LateShow",
                            start = lateStart.toString(),
                            duration = "1800",
                            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
                            serviceName = "Das Erste HD",
                        ),
                        startSec = lateStart,
                        endSec = lateStart + 1800,
                    ),
                ),
            ),
        )
        var visibleMinutes by mutableIntStateOf(MULTI_EPG_VISIBLE_MINUTES)
        var spanSec = 0L
        composeRule.setContent {
            DreamDroidTheme {
                MultiEpgScreen(
                    bouquetName = "Favourites",
                    channels = channels,
                    timelineStartSec = start,
                    timelineEndSec = start + 6L * 3600L,
                    nowSec = start + 60,
                    loading = false,
                    errorMessage = null,
                    onJumpToNow = {},
                    onEventClick = {},
                    onVisibleWindow = { visStart, visEnd ->
                        spanSec = visEnd - visStart
                    },
                    visibleMinutes = visibleMinutes,
                    onVisibleMinutesChange = { visibleMinutes = it },
                )
            }
        }
        composeRule.waitUntil(5_000) { spanSec > 0L }
        val spanAt2h = spanSec
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("LateShow").assertDoesNotExist()
        val dayBefore = dayLabelText()

        composeRule.runOnIdle { visibleMinutes = 300 }
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) { spanSec > spanAt2h }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("LateShow").assertIsDisplayed()
        assertEquals(dayBefore, dayLabelText())
        assertTrue(spanSec > spanAt2h * 2)
    }

    @Test
    fun buildChannelsDedupesTheSameEventFromAdjacentWindows() {
        val event = Event(
            eventId = "span",
            title = "Overnight",
            start = "1000",
            duration = "90000",
            serviceReference = "1:0:1:1:1:1:0:0:0:0:",
            serviceName = "Das Erste",
        )
        val channels = buildMultiEpgChannels(listOf(event, event.copy()))
        assertEquals(1, channels.size)
        assertEquals(1, channels[0].bars.size)
        assertEquals("Overnight", channels[0].bars[0].event.title)
    }

    @Test
    fun timeRulerStaysVisibleAfterVerticalFling() {
        val start = 1_700_000_000L
        val channels = (0 until 24).map { index ->
            MultiEpgChannel(
                serviceRef = "1:0:1:$index:1:1:0:0:0:0:",
                serviceName = "Channel $index",
                bars = listOf(
                    MultiEpgBar(
                        event = Event(
                            eventId = "$index",
                            title = "Show $index",
                            start = start.toString(),
                            duration = "1800",
                            serviceReference = "1:0:1:$index:1:1:0:0:0:0:",
                            serviceName = "Channel $index",
                        ),
                        startSec = start,
                        endSec = start + 1800,
                    ),
                ),
            )
        }
        composeRule.setContent {
            DreamDroidTheme {
                Box(modifier = Modifier.height(220.dp)) {
                    MultiEpgScreen(
                        bouquetName = "Favourites",
                        channels = channels,
                        timelineStartSec = start,
                        timelineEndSec = start + 7200,
                        nowSec = start + 60,
                        loading = false,
                        errorMessage = null,
                        onJumpToNow = {},
                        onEventClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag("multi_epg_time_ruler").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_epg_channel_list").performScrollToIndex(23)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Channel 23").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_epg_time_ruler").assertIsDisplayed()
    }
}
