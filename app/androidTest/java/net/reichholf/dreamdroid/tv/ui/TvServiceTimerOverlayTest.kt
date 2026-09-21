package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvServiceTimerOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overlayHostsStreamSetEditAndEventTags() {
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvServiceTimerOverlay(
                        service = demoService(),
                        onDismiss = {},
                        onStream = {},
                        onSetTimer = {},
                        onEditTimer = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag("tv_service_timer_overlay").assertExists()
        composeRule.onNodeWithTag("tv_service_timer_overlay_stream").assertExists()
        composeRule.onNodeWithTag("tv_service_timer_overlay_set_timer").assertExists()
        composeRule.onNodeWithTag("tv_service_timer_overlay_edit_timer").assertExists()
        composeRule.onNodeWithTag("tv_service_timer_overlay_current").assertExists()
        composeRule.onNodeWithTag("tv_service_timer_overlay_next").assertExists()
    }

    @Test
    fun streamSetAndEditInvokeCallbacksForCurrentEvent() {
        var streamed = false
        var setEvent: Event? = null
        var editEvent: Event? = null
        val service = demoService()
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvServiceTimerOverlay(
                        service = service,
                        onDismiss = {},
                        onStream = { streamed = true },
                        onSetTimer = { setEvent = it },
                        onEditTimer = { editEvent = it }
                    )
                }
            }
        }
        dpadActivate("tv_service_timer_overlay_stream") { streamed }
        assertEquals(true, streamed)
        dpadActivate("tv_service_timer_overlay_set_timer") { setEvent != null }
        assertEquals(service.now, setEvent)
        dpadActivate("tv_service_timer_overlay_edit_timer") { editEvent != null }
        assertEquals(service.now, editEvent)
    }

    @Test
    fun selectingNextUsesNextEventForSetAndEdit() {
        var setEvent: Event? = null
        var editEvent: Event? = null
        val service = demoService()
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvServiceTimerOverlay(
                        service = service,
                        onDismiss = {},
                        onStream = {},
                        onSetTimer = { setEvent = it },
                        onEditTimer = { editEvent = it }
                    )
                }
            }
        }
        dpadActivate("tv_service_timer_overlay_next") { false }
        dpadActivate("tv_service_timer_overlay_set_timer") { setEvent != null }
        assertEquals(service.next, setEvent)
        dpadActivate("tv_service_timer_overlay_edit_timer") { editEvent != null }
        assertEquals(service.next, editEvent)
    }

    @Test
    fun streamWhenDisabledShowsNeedsReceiverInsteadOfCallback() {
        var streamed = false
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvServiceTimerOverlay(
                        service = demoService(),
                        onDismiss = {},
                        onStream = { streamed = true },
                        onSetTimer = {},
                        onEditTimer = {},
                        streamingEnabled = false
                    )
                }
            }
        }
        dpadActivate("tv_service_timer_overlay_stream") {
            composeRule.onAllNodesWithTag("tv_service_timer_overlay_needs_receiver")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertEquals(false, streamed)
        composeRule.onNodeWithTag("tv_service_timer_overlay_needs_receiver").assertExists()
    }

    @Test
    fun setTimerWhenMutationsBlockedShowsNeedsReceiver() {
        var setEvent: Event? = null
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvServiceTimerOverlay(
                        service = demoService(),
                        onDismiss = {},
                        onStream = {},
                        onSetTimer = { setEvent = it },
                        onEditTimer = {},
                        mutationsBlocked = true
                    )
                }
            }
        }
        dpadActivate("tv_service_timer_overlay_set_timer") {
            composeRule.onAllNodesWithTag("tv_service_timer_overlay_needs_receiver")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertNull(setEvent)
        composeRule.onNodeWithTag("tv_service_timer_overlay_needs_receiver").assertExists()
    }

    @Test
    fun hidesSetEditWhenNowAndNextMissing() {
        composeRule.setContent {
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvServiceTimerOverlay(
                        service = ServiceNowNext(
                            serviceReference = "1:0:1:1:1:1:1:0:0:0:",
                            serviceName = "Demo Channel"
                        ),
                        onDismiss = {},
                        onStream = {},
                        onSetTimer = {},
                        onEditTimer = {}
                    )
                }
            }
        }
        composeRule.onNodeWithTag("tv_service_timer_overlay_stream").assertExists()
        composeRule.onAllNodesWithTag("tv_service_timer_overlay_set_timer")
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("tv_service_timer_overlay_edit_timer")
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("tv_service_timer_overlay_current")
            .assertCountEquals(0)
        composeRule.onAllNodesWithTag("tv_service_timer_overlay_next")
            .assertCountEquals(0)
    }

    private fun dpadActivate(tag: String, invoked: () -> Boolean) {
        val node = composeRule.onNodeWithTag(tag)
        node.assertIsDisplayed().assertHasClickAction()
        node.requestFocus()
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        if (!invoked()) {
            node.performClick()
            composeRule.waitForIdle()
        }
    }

    private fun demoService() = ServiceNowNext(
        serviceReference = "1:0:1:1:1:1:1:0:0:0:",
        serviceName = "Demo Channel",
        now = Event(eventId = "10", title = "Now Show"),
        next = Event(eventId = "11", title = "Next Show")
    )
}
