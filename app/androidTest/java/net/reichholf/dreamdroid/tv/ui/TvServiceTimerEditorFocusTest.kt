package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.ui.timers.TimerEditUiState
import net.reichholf.dreamdroid.ui.timers.TimerFormActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Opening the timer editor from the hub service INFO overlay must not change the header.
 * The overlay leaves the composition while its focused action is removed; focus must not
 * fall back to the drawer and select the first bouquet.
 */
@OptIn(ExperimentalTestApi::class)
class TvServiceTimerEditorFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openingTheEditorFromTheServiceOverlayKeepsTheSelectedBouquet() {
        val favourites = HubBouquetRow(
            bouquet = Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites"),
            services = emptyList()
        )
        val channel = ServiceNowNext(
            serviceReference = "1:0:1:1:1:1:1:0:0:0:",
            serviceName = "Das Erste",
            now = Event(title = "Tatort", start = "1700000000", duration = "3600")
        )
        val sports = HubBouquetRow(
            bouquet = Service("1:7:1:0:0:0:0:0:0:0:Sports", "Sports"),
            services = listOf(channel)
        )
        var selected by mutableStateOf(sports.bouquet.reference)
        var overlay by mutableStateOf<ServiceNowNext?>(null)
        var editingEvent by mutableStateOf<Event?>(null)

        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            Box(Modifier.fillMaxSize()) {
                ComposeTvHubChrome(
                    headers = listOf(
                        HubNavHeader(favourites.bouquet.reference, favourites.bouquet.name),
                        HubNavHeader(sports.bouquet.reference, sports.bouquet.name),
                        HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timer")
                    ),
                    selectedHeaderId = selected,
                    onHeaderSelected = { selected = it },
                    settingsItems = emptyList(),
                    onSettingsClick = {},
                    bouquetRows = listOf(favourites, sports),
                    onServiceInfo = { service, _ -> overlay = service }
                )
                val target = overlay
                if (target != null && editingEvent == null) {
                    TvServiceTimerOverlay(
                        service = target,
                        onDismiss = { overlay = null },
                        onStream = {},
                        onSetTimer = {},
                        onEditTimer = { editingEvent = it }
                    )
                }
                val event = editingEvent
                if (event != null) {
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
                        onDismiss = { editingEvent = null }
                    )
                }
            }
        }

        // Switches and other clickables take focus only outside touch mode, as on a TV
        // driven by the remote.
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }

        val card = composeRule.onNodeWithTag("hub_service_card")
        card.requestFocus()
        card.performKeyInput { pressKey(Key.Info) }
        composeRule.waitForIdle()

        val edit = composeRule.onNodeWithTag("tv_service_timer_overlay_edit_timer")
        edit.requestFocus()
        edit.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertEquals("header selection changed", sports.bouquet.reference, selected)
        // Focus lands on the first control that is not a text field, so no soft keyboard.
        composeRule.onNodeWithContentDescription(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.enabled)
        ).assertIsFocused()
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed().assertIsNotFocused()
    }
}
