package net.reichholf.dreamdroid.ui.epg

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A service's schedule in the services hub's detail pane, with its own top app bar. */
class ServiceEpgPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun namesTheServiceAndOpensAnEvent() {
        var clicked: Event? = null
        show(onEventClick = { clicked = it })

        composeRule.onNodeWithText(
            context.getString(R.string.title_with_status, context.getString(R.string.epg), "ZDF")
        ).assertIsDisplayed()
        composeRule.onNode(hasText("heute") and hasClickAction()).performClick()

        assertEquals("heute", clicked?.title)
    }

    @Test
    fun closeDismissesThePane() {
        var closed = false
        show(onClose = { closed = true })

        composeRule.onNodeWithContentDescription(context.getString(R.string.close)).performClick()

        assertTrue(closed)
    }

    private fun show(onEventClick: (Event) -> Unit = {}, onClose: () -> Unit = {}) {
        val event = Event(
            eventId = "1",
            title = "heute",
            startTimeReadable = "19:00",
            durationReadable = "20"
        )
        composeRule.setContent {
            DreamDroidTheme {
                ServiceEpgPane(
                    state = ServiceEpgUiState(
                        serviceRef = "1:0:1:2B66:3F3:1:C00000:0:0:0:",
                        serviceName = "ZDF",
                        sections = listOf(
                            EpgDaySection(UiText.Resource(R.string.today), listOf(event))
                        )
                    ),
                    onRefresh = {},
                    onEventClick = onEventClick,
                    onClose = onClose
                )
            }
        }
    }
}
