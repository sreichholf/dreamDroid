package net.reichholf.dreamdroid.ui.current

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CurrentServiceScreenTest {
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
    fun notReadyShowsLoadingPlaceholders() {
        val state = CurrentServiceUiState()
        composeRule.setContent {
            DreamDroidTheme {
                CurrentServiceScreen(
                    state = state,
                    onNowClick = {},
                    onNextClick = {},
                    onStream = {},
                )
            }
        }
        composeRule.onAllNodesWithText("Loading", substring = true).fetchSemanticsNodes().let {
            assertTrue("expected Loading placeholders", it.isNotEmpty())
        }
    }

    @Test
    fun readyWithBlankNowDoesNotShowLoading() {
        val current = CurrentService(
            service = Service(
                reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
                name = "Das Erste HD",
                provider = "ARD",
            ),
            now = null,
            next = Event(
                eventId = "39151",
                title = "Wetter",
                startReadable = "20:15",
                durationReadable = "15",
                descriptionExtended = "Der Wetterbericht.",
            ),
        )
        val state = CurrentServiceUiState().apply { apply(current) }
        composeRule.setContent {
            DreamDroidTheme {
                CurrentServiceScreen(
                    state = state,
                    onNowClick = {},
                    onNextClick = {},
                    onStream = {},
                )
            }
        }
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").assertIsDisplayed()
        composeRule.onNodeWithText("Loading").assertDoesNotExist()
    }

    @Test
    fun seededCurrentServiceShowsServiceNowNextAndStream() {
        val current = CurrentService(
            service = Service(
                reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
                name = "Das Erste HD",
                provider = "ARD",
            ),
            now = Event(
                eventId = "39150",
                title = "Tagesschau",
                startReadable = "20:00",
                durationReadable = "60",
                descriptionExtended = "Die Nachrichten um 20 Uhr.",
            ),
            next = Event(
                eventId = "39151",
                title = "Wetter",
                startReadable = "20:15",
                durationReadable = "15",
                descriptionExtended = "Der Wetterbericht.",
            ),
        )
        val state = CurrentServiceUiState().apply { apply(current) }
        var streamClicks = 0
        composeRule.setContent {
            DreamDroidTheme {
                CurrentServiceScreen(
                    state = state,
                    onNowClick = {},
                    onNextClick = {},
                    onStream = { streamClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("Service").assertIsDisplayed()
        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Provider").assertIsDisplayed()
        composeRule.onNodeWithText("ARD").assertIsDisplayed()
        composeRule.onNodeWithText("Now").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("20:00").assertIsDisplayed()
        composeRule.onNodeWithText("60").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").assertIsDisplayed()
        composeRule.onNodeWithText("20:15").assertIsDisplayed()
        composeRule.onNodeWithText("Stream current").assertIsDisplayed().performClick()
        assertTrue(streamClicks == 1)
    }
}
