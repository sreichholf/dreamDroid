package net.reichholf.dreamdroid.ui.current

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NowPlayingDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .commit()
    }

    @Test
    fun nowNextAndStreamMatchEpgLayoutWithoutTimerActions() {
        val current = CurrentService(
            service = Service(
                reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
                name = "Das Erste HD",
                provider = "ARD"
            ),
            now = Event(
                eventId = "39150",
                title = "Tagesschau",
                startReadable = "20:00",
                durationReadable = "15",
                description = "News",
                descriptionExtended = "Die Nachrichten um 20 Uhr."
            ),
            next = Event(
                eventId = "39151",
                title = "Wetter",
                startReadable = "20:15",
                durationReadable = "15",
                descriptionExtended = "Der Wetterbericht."
            )
        )
        var streamClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                NowPlayingDetailScreen(
                    current = current,
                    onStream = { streamClicks++ }
                )
            }
        }
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertIsDisplayed()
        composeRule.onNodeWithText("20:00 (15 min.)").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").assertIsDisplayed()
        composeRule.onNodeWithText("Der Wetterbericht.").assertIsDisplayed()
        composeRule.onNodeWithText("Set Timer").assertDoesNotExist()
        composeRule.onNodeWithText("IMDb").assertDoesNotExist()
        composeRule.onNodeWithText("Stream current").assertIsDisplayed().performClick()
        assertEquals(1, streamClicks)
    }

    @Test
    fun loadingWithoutRefDoesNotOfferStream() {
        var streamClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                NowPlayingDetailScreen(
                    current = null,
                    loading = true,
                    onStream = { streamClicks++ }
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.loading)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.not_available)).assertDoesNotExist()
        composeRule.onNodeWithText("Stream current").assertDoesNotExist()
        assertEquals(0, streamClicks)
    }

    @Test
    fun failedWithoutRefDoesNotOfferStream() {
        var streamClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                NowPlayingDetailScreen(
                    current = null,
                    onStream = { streamClicks++ }
                )
            }
        }
        composeRule.onNodeWithText("Not available").assertIsDisplayed()
        composeRule.onNodeWithText("Stream current").assertDoesNotExist()
        assertEquals(0, streamClicks)
    }

    @Test
    fun lastGoodWithoutNowStillShowsStream() {
        val current = CurrentService(
            service = Service(
                reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
                name = "Das Erste HD"
            )
        )
        var streamClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                NowPlayingDetailScreen(
                    current = current,
                    onStream = { streamClicks++ }
                )
            }
        }
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Stream current").assertIsDisplayed().performClick()
        assertEquals(1, streamClicks)
    }
}
