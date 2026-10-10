package net.reichholf.dreamdroid.ui.epg

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** An EPG event in a list-detail pane of the services hub, with a close button. */
class EpgEventPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsTheEventOnce() {
        show()

        // The bar carries only the close button; the detail names the service.
        composeRule.onNodeWithText("ZDF").assertIsDisplayed()
        composeRule.onNodeWithText("heute").assertIsDisplayed()
    }

    @Test
    fun closeDismissesThePane() {
        var closed = false
        show(onClose = { closed = true })

        composeRule.onNodeWithContentDescription(context.getString(R.string.close)).performClick()

        assertTrue(closed)
    }

    private fun show(onClose: () -> Unit = {}) {
        composeRule.setContent {
            DreamDroidTheme {
                EpgEventPane(
                    state = EpgEventDetailUiState(),
                    event = Event(
                        eventId = "1",
                        title = "heute",
                        serviceName = "ZDF",
                        startTimeReadable = "19:00",
                        durationReadable = "20"
                    ),
                    actions = EpgEventActions({}, {}, {}, {}, {}),
                    onClose = onClose
                )
            }
        }
    }
}
