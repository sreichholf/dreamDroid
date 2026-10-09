package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.timers.TimerEditUiState
import net.reichholf.dreamdroid.ui.timers.TimerFormActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Opening the hub Timers editor must keep the Timers header selected and put focus in the
 * form. The edit button leaves the composition, so the editor takes focus itself; focus
 * that falls back to the drawer would leave the form unreachable.
 */
@OptIn(ExperimentalTestApi::class)
class TvTimerEditorHubFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openingTheTimerEditorKeepsTheTimersHeaderSelected() {
        val bouquet = HubBouquetRow(
            bouquet = Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites"),
            services = emptyList()
        )
        val timers = listOf(Timer(name = "Existing", serviceName = "ARD"))
        var selected by mutableStateOf(TvComposeHubHost.HEADER_TIMERS_ID)
        var page by mutableStateOf<TvTimerPage>(TvTimerPage.List)

        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            ComposeTvHubChrome(
                headers = listOf(
                    HubNavHeader(bouquet.bouquet.reference, bouquet.bouquet.name),
                    HubNavHeader(TvComposeHubHost.HEADER_TIMERS_ID, "Timer")
                ),
                selectedHeaderId = selected,
                onHeaderSelected = { selected = it },
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = listOf(bouquet),
                timerContent = {
                    TvTimerHostContent(
                        uiState = TvTimerHostUiState(
                            page = page,
                            editorTimer = if (page is TvTimerPage.List) null else timers[0],
                            timers = timers,
                            emptyMessage = null
                        ),
                        onAdd = {},
                        onEdit = { page = TvTimerPage.Edit(it) },
                        onToggleEnabled = {},
                        onDelete = {},
                        onShowList = { page = TvTimerPage.List }
                    ) { timer, isCreate ->
                        TvTimerEditorContent(
                            uiState = TimerEditUiState(timer = timer, isCreate = isCreate),
                            name = remember { TextFieldState(timer.name) },
                            description = remember { TextFieldState(timer.description) },
                            actions = object : TimerFormActions {},
                            servicePickKey = tvTimerServicePickKey(null),
                            onServicePicked = {},
                            onSave = {},
                            onDismiss = {}
                        )
                    }
                }
            )
        }

        // Switches and other clickables take focus only outside touch mode, as on a TV
        // driven by the remote.
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle { assertEquals(InputMode.Keyboard, inputModeManager.inputMode) }

        val edit = composeRule.onNodeWithTag("tv_timers_edit_0")
        edit.requestFocus()
        composeRule.waitForIdle()
        edit.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertEquals("header selection changed", TvComposeHubHost.HEADER_TIMERS_ID, selected)
        // Focus lands on the first control that is not a text field, so no soft keyboard.
        composeRule.onNodeWithContentDescription(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.enabled)
        ).assertIsFocused()
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed().assertIsNotFocused()
    }
}
