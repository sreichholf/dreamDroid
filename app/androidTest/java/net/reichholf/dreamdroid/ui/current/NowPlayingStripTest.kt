package net.reichholf.dreamdroid.ui.current

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Event
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
}
