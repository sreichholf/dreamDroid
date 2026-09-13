package net.reichholf.dreamdroid.ui.multiepg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.buildMultiEpgChannels
import net.reichholf.dreamdroid.ui.compose.PULL_REFRESH_INDICATOR_TAG
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
        composeRule.onNodeWithText("Favourites").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("ZDF HD").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").performClick()
        composeRule.waitForIdle()
        assertEquals("Tagesschau", clicked)
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

    private fun dayLabelText(): String {
        val node = composeRule.onNodeWithTag("multi_epg_day_label").fetchSemanticsNode()
        return node.config[SemanticsProperties.Text].joinToString { it.text }
    }
}
