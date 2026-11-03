package net.reichholf.dreamdroid.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.multiepg.MultiEpgBar
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.timers.TimerEditUiState
import net.reichholf.dreamdroid.ui.timers.TimerFormActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * D-pad focus must stay in a modal overlay and go back to the opener when it closes. The overlay
 * cancels a move that would leave it, so the focused action does not change and the actions behind
 * the overlay never take focus. Keyboard input mode is required: a TV Surface focuses in touch mode
 * too, so a test that only requests focus would not prove the overlay traps a real D-pad move.
 */
@OptIn(ExperimentalTestApi::class)
class TvFocusTrapTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var inputModeManager: InputModeManager

    /** Focus the background reports; a cancelled move must never get there. */
    private var backgroundFocusCount = 0

    @Test
    fun serviceTimerOverlayKeepsFocusAtItsLastAction() {
        setTvContent {
            BackgroundActions()
            TvServiceTimerOverlay(
                service = demoService(),
                onDismiss = {},
                onStream = {},
                onSetTimer = {},
                onEditTimer = {}
            )
        }
        val last = composeRule.onNodeWithTag("tv_service_timer_overlay_edit_timer")
        last.requestFocus()
        composeRule.waitForIdle()
        last.assertIsFocused()
        backgroundFocusCount = 0

        last.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        last.assertIsFocused()
        assertBackgroundNeverFocused()

        val first = composeRule.onNodeWithTag("tv_service_timer_overlay_stream")
        first.requestFocus()
        composeRule.waitForIdle()
        first.assertIsFocused()
        backgroundFocusCount = 0

        first.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        first.assertIsFocused()
        assertBackgroundNeverFocused()
    }

    @Test
    fun serviceTimerOverlayMovesBetweenItsActions() {
        setTvContent {
            BackgroundActions()
            TvServiceTimerOverlay(
                service = demoService(),
                onDismiss = {},
                onStream = {},
                onSetTimer = {},
                onEditTimer = {}
            )
        }
        val first = composeRule.onNodeWithTag("tv_service_timer_overlay_stream")
        first.requestFocus()
        composeRule.waitForIdle()
        first.assertIsFocused()
        backgroundFocusCount = 0

        first.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tv_service_timer_overlay_current").assertIsFocused()
        first.assertIsNotFocused()
        assertBackgroundNeverFocused()
    }

    @Test
    fun needsReceiverOverlayKeepsFocusOnItsOnlyAction() {
        setTvContent {
            BackgroundActions()
            TvNeedsReceiverOverlay(onDismiss = {}, testTag = "trap_needs_receiver")
        }
        val ok = composeRule.onNodeWithTag("trap_needs_receiver_ok")
        ok.requestFocus()
        composeRule.waitForIdle()
        ok.assertIsFocused()
        backgroundFocusCount = 0

        ok.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        ok.assertIsFocused()

        ok.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()
        ok.assertIsFocused()
        assertBackgroundNeverFocused()
    }

    @Test
    fun multiEpgDetailKeepsFocusAtItsFirstAction() {
        setTvContent {
            BackgroundActions()
            TvMultiEpgEventDetail(
                event = demoEvent(),
                progress = null,
                onDismiss = {}
            )
        }
        val first = composeRule.onNodeWithTag("tv_multi_epg_detail_stream")
        first.requestFocus()
        composeRule.waitForIdle()
        first.assertIsFocused()
        backgroundFocusCount = 0

        first.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        first.assertIsFocused()
        assertBackgroundNeverFocused()
    }

    @Test
    fun timerServicePickKeepsFocusAtItsLastRow() {
        setTvContent {
            BackgroundActions()
            TvTimerServicePickScreen(
                rows = listOf(
                    Service("1:0:1:1:0:0:0:0:0:0:", "Das Erste"),
                    Service("1:0:1:2:0:0:0:0:0:0:", "ZDF")
                ),
                emptyMessage = null,
                onRowClick = {}
            )
        }
        val last = composeRule.onNodeWithTag("tv_timer_service_pick_row_1")
        last.requestFocus()
        composeRule.waitForIdle()
        last.assertIsFocused()
        backgroundFocusCount = 0

        last.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        last.assertIsFocused()
        assertBackgroundNeverFocused()
    }

    @Test
    fun timerEditorKeepsFocusInItsForm() {
        val event = demoEvent()
        setTvContent {
            BackgroundActions()
            TvTimerEditorContent(
                uiState = TimerEditUiState(
                    timer = Timer.createByEvent(event),
                    isCreate = true
                ),
                name = remember { TextFieldState(event.title) },
                description = remember { TextFieldState("") },
                actions = object : TimerFormActions {},
                servicePickKey = tvTimerServicePickKey(null),
                onServicePicked = {},
                onSave = {},
                onDismiss = {},
                trapFocus = true
            )
        }
        val title = composeRule.onNodeWithContentDescription("Title")
        title.requestFocus()
        composeRule.waitForIdle()
        title.assertIsFocused()
        backgroundFocusCount = 0

        title.performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        title.assertIsFocused()
        assertBackgroundNeverFocused()
    }

    @Test
    fun hubStreamOverlayKeepsFocusAndRestoresTheCard() {
        val bouquetRef = "1:7:1:0:0:0:0:0:0:0:Favourites"
        setTvContent {
            ComposeTvHubChrome(
                section = HubSection.Live(bouquetRef),
                onOpenTab = {},
                onOpenSettings = {},
                onDrawerItemSelected = {},
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = listOf(
                    HubBouquetRow(
                        bouquet = Service(bouquetRef, "Favourites"),
                        services = listOf(
                            demoService("1:0:1:1:1:1:1:0:0:0:", "Das Erste"),
                            demoService("1:0:1:2:1:1:1:0:0:0:", "ZDF")
                        )
                    )
                ),
                streamingEnabled = false
            )
        }
        composeRule.waitForIdle()
        val cards = composeRule.onAllNodesWithTag("hub_service_card")
        cards[0].assertIsFocused()
        cards[1].requestFocus()
        cards[1].performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hub_stream_unavailable").assertIsDisplayed()

        val ok = composeRule.onNodeWithTag("hub_stream_unavailable_ok")
        ok.assertIsFocused()
        ok.performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        ok.assertIsFocused()
        cards[1].assertIsNotFocused()

        ok.performClick()
        composeRule.waitForIdle()

        cards[1].assertIsFocused()
        cards[0].assertIsNotFocused()
    }

    @Test
    fun multiEpgGridTakesFocusBackWhenTheDetailDismisses() {
        // Mirrors TvMultiEpgHost: keysEnabled follows the detail overlay, and Back dismisses it.
        val start = 1_700_000_000L
        val channel = MultiEpgChannel(
            serviceRef = "1:0:1:1:0:0:0:0:0:0:",
            serviceName = "Das Erste",
            bars = listOf(
                MultiEpgBar(
                    event = Event(
                        eventId = "1",
                        title = "News",
                        start = start.toString(),
                        duration = "3600"
                    ),
                    startSec = start,
                    endSec = start + 3600
                )
            ),
            occurrence = 0
        )
        val event = demoEvent()
        var detail by mutableStateOf<Event?>(null)
        setTvContent {
            TvMultiEpgScreen(
                bouquetName = "Favourites",
                channels = listOf(channel),
                timelineStartSec = start,
                timelineEndSec = start + 4 * 3600,
                nowSec = start,
                originFloorSec = start,
                loading = false,
                errorMessage = null,
                selectedKey = channel.key,
                selectedStartSec = start,
                onSelectedChange = { _, _ -> },
                onJumpToNow = {},
                onPrevDay = {},
                onNextDay = {},
                onVisibleWindow = { _, _ -> },
                onEventClick = { detail = it },
                onBouquetClick = {},
                keysEnabled = detail == null
            )
            detail?.let {
                TvMultiEpgEventDetail(
                    event = it,
                    progress = null,
                    onDismiss = { detail = null }
                )
            }
        }

        composeRule.onNodeWithTag("tv_multi_epg_grid").requestFocus()
        composeRule.waitForIdle()
        composeRule.runOnIdle { detail = event }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_multi_epg_detail").assertIsDisplayed()

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tv_multi_epg_detail").assertDoesNotExist()
        composeRule.onNodeWithTag("tv_multi_epg_grid").assertIsFocused()
    }

    private fun setTvContent(content: @Composable () -> Unit) {
        backgroundFocusCount = 0
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            DreamDroidTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }
    }

    private fun assertBackgroundNeverFocused() {
        assertEquals("focus landed behind the overlay", 0, backgroundFocusCount)
        composeRule.onNodeWithTag("trap_background_top").assertIsNotFocused()
        composeRule.onNodeWithTag("trap_background_bottom").assertIsNotFocused()
    }

    @Composable
    private fun BackgroundActions() {
        Box(modifier = Modifier.fillMaxSize()) {
            FocusableBackground(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("trap_background_top")
            )
            FocusableBackground(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .testTag("trap_background_bottom")
            )
        }
    }

    @Composable
    private fun FocusableBackground(modifier: Modifier) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .onFocusChanged { if (it.isFocused) backgroundFocusCount++ }
                .clickable {}
        )
    }

    private fun demoService(
        reference: String = "1:0:1:1:1:1:1:0:0:0:",
        name: String = "Demo Channel"
    ) = ServiceNowNext(
        serviceReference = reference,
        serviceName = name,
        now = Event(eventId = "10", title = "Now Show"),
        next = Event(eventId = "11", title = "Next Show")
    )

    private fun demoEvent() = Event(
        eventId = "1",
        title = "News",
        start = "1700000000",
        duration = "3600",
        serviceReference = "1:0:1:1:0:0:0:0:0:0:",
        serviceName = "Das Erste"
    )
}
