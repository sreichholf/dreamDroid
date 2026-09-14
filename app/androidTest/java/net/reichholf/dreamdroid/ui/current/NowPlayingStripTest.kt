package net.reichholf.dreamdroid.ui.current

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NowPlayingStripTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .putBoolean(DreamDroid.PREFS_KEY_PICONS_ENABLED, false)
            .commit()
    }

    @Test
    fun seededStripShowsNowLabelHeadlineAndClick() {
        var clicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                NowPlayingStrip(
                    label = "Now",
                    headline = "Das Erste HD · Tagesschau",
                    progress = 0.4f,
                    serviceReference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
                    serviceName = "Das Erste HD",
                    onClick = { clicks++ },
                )
            }
        }
        composeRule.onNodeWithText("Now").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD · Tagesschau").assertIsDisplayed()
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun headlineJoinsServiceAndTitleWhenReady() {
        assertEquals(
            "Das Erste HD · Tagesschau",
            nowPlayingHeadline(
                ready = true,
                serviceName = "Das Erste HD",
                eventTitle = "Tagesschau",
                loadingText = "Loading",
                unavailableText = "Not available",
            ),
        )
        assertEquals(
            "Loading",
            nowPlayingHeadline(
                ready = false,
                serviceName = "Das Erste HD",
                eventTitle = "Tagesschau",
                loadingText = "Loading",
                unavailableText = "Not available",
            ),
        )
        assertEquals(
            "Not available",
            nowPlayingHeadline(
                ready = true,
                serviceName = "",
                eventTitle = "",
                loadingText = "Loading",
                unavailableText = "Not available",
            ),
        )
    }

    @Test
    fun progressIsZeroWithoutDuration() {
        assertEquals(0f, eventProgressFraction(null))
        assertEquals(
            0f,
            eventProgressFraction(
                Event(title = "Tagesschau", duration = Python.NONE, start = "1"),
            ),
        )
        val mid = eventProgressFraction(
            Event(
                title = "Tagesschau",
                duration = "3600",
                start = "1000",
                currentTime = "2800",
            ),
        )
        assertTrue("expected mid-event progress, got $mid", mid > 0f && mid < 1f)
    }

    @Test
    fun loadingStripClickDoesNotOfferStreamOnEmptyRef() {
        var streamClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                var showSheet by remember { mutableStateOf(false) }
                Column {
                    NowPlayingStrip(
                        label = "Now",
                        headline = "Loading",
                        progress = 0f,
                        serviceReference = "",
                        serviceName = "",
                        onClick = { showSheet = true },
                    )
                    if (showSheet) {
                        NowPlayingDetailScreen(
                            current = null,
                            loading = true,
                            onStream = { streamClicks++ },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Loading").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Stream current").assertDoesNotExist()
        composeRule.onNodeWithText("Not available").assertDoesNotExist()
        assertEquals(0, streamClicks)
    }

    @Test
    fun failedStripClickDoesNotOfferStreamOnEmptyRef() {
        var streamClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                var showSheet by remember { mutableStateOf(false) }
                Column {
                    NowPlayingStrip(
                        label = "Now",
                        headline = "Not available",
                        progress = 0f,
                        serviceReference = "",
                        serviceName = "",
                        onClick = { showSheet = true },
                    )
                    if (showSheet) {
                        NowPlayingDetailScreen(
                            current = null,
                            loading = false,
                            onStream = { streamClicks++ },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Not available").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Stream current").assertDoesNotExist()
        assertEquals(0, streamClicks)
    }

    @Test
    fun lastGoodStripClickShowsStream() {
        val current = CurrentService(
            service = Service(
                reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
                name = "Das Erste HD",
            ),
            now = Event(title = "Tagesschau"),
        )
        var streamClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                var showSheet by remember { mutableStateOf(false) }
                Column {
                    NowPlayingStrip(
                        label = "Now",
                        headline = "Das Erste HD · Tagesschau",
                        progress = 0.4f,
                        serviceReference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
                        serviceName = "Das Erste HD",
                        onClick = { showSheet = true },
                    )
                    if (showSheet) {
                        NowPlayingDetailScreen(
                            current = current,
                            onStream = { streamClicks++ },
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("Das Erste HD · Tagesschau").assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Stream current").assertIsDisplayed().performClick()
        assertEquals(1, streamClicks)
    }
}
