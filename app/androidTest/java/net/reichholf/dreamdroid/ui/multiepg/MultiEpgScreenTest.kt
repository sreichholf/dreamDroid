package net.reichholf.dreamdroid.ui.multiepg

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
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
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
}
