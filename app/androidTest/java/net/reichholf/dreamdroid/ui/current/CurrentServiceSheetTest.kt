package net.reichholf.dreamdroid.ui.current

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
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

class CurrentServiceSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun forceAlwaysNight() {
        PreferenceManager.getDefaultSharedPreferences(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).edit()
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "1")
            .commit()
    }

    @Test
    fun sheetShowsServiceAndNowNextDetails() {
        show(current = currentService())

        composeRule.onNodeWithText("Das Erste HD").assertIsDisplayed()
        composeRule.onNodeWithText("Tagesschau").assertIsDisplayed()
        composeRule.onNodeWithText("20:00 (15 min.)").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertIsDisplayed()
        composeRule.onNodeWithText("Die Nachrichten um 20 Uhr.").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.next)).assertIsDisplayed()
        composeRule.onNodeWithText("Wetter").assertIsDisplayed()
        composeRule.onNodeWithText("20:15 (15 min.)").assertIsDisplayed()
        composeRule.onNodeWithText("Der Wetterbericht.").assertIsDisplayed()
    }

    @Test
    fun streamRunsOnStreamBeforeOnDismiss() {
        val calls = mutableListOf<String>()
        show(
            current = currentService(),
            onStream = { calls += "stream" },
            onDismiss = { calls += "dismiss" }
        )

        composeRule.onNodeWithText("Stream current").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("stream", "dismiss"), calls)
    }

    @Test
    fun loadingWithoutServiceShowsTheLoadingIndication() {
        show(current = null, loading = true)

        composeRule.onNodeWithText(context.getString(R.string.loading)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.not_available)).assertDoesNotExist()
        composeRule.onNodeWithText("Stream current").assertDoesNotExist()
    }

    @Test
    fun blockedStreamExplainsWhyItIsUnavailable() {
        show(current = currentService(), streamBlocked = true)

        val stream = composeRule.onNodeWithText("Stream current").assertIsDisplayed()
        stream.assert(hasStateDescription(context.getString(R.string.session_needs_receiver)))
        stream.assertHasClickAction()
    }

    private fun show(
        current: CurrentService?,
        loading: Boolean = false,
        streamBlocked: Boolean = false,
        onStream: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeRule.setContent {
            DreamDroidTheme {
                CurrentServiceSheet(
                    current = current,
                    loading = loading,
                    streamBlocked = streamBlocked,
                    onStream = onStream,
                    onDismiss = onDismiss
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun currentService() = CurrentService(
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
}
