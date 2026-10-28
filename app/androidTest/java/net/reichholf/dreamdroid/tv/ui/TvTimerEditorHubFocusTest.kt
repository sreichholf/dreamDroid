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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.theme.DreamDroidTvTheme
import net.reichholf.dreamdroid.ui.timers.TimerEditUiState
import net.reichholf.dreamdroid.ui.timers.TimerFormActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Opening the hub Timers editor must keep Timers selected and put focus in the form. The
 * edit button leaves the composition, so the editor takes focus itself; focus that falls
 * back to the top bar would leave the form unreachable or switch the tab. A tab switch that
 * reveals an already open editor is different: focus stays on the bar until Down.
 */
@OptIn(ExperimentalTestApi::class)
class TvTimerEditorHubFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val bouquet = HubBouquetRow(
        bouquet = Service("1:7:1:0:0:0:0:0:0:0:Favourites", "Favourites"),
        services = emptyList()
    )
    private val timers = listOf(Timer(name = "Existing", serviceName = "ARD"))

    private class HubState(initialPage: TvTimerPage) {
        var selected by mutableStateOf<HubSection>(HubSection.Timers)
        var page by mutableStateOf(initialPage)
        lateinit var inputModeManager: InputModeManager
    }

    @Test
    fun openingTheTimerEditorKeepsTimersSelectedAndFocusesTheNameField() {
        val state = showHub(TvTimerPage.List)

        val edit = composeRule.onNodeWithTag("tv_timers_edit_0")
        edit.requestFocus()
        composeRule.waitForIdle()
        edit.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertEquals("section changed", HubSection.Timers, state.selected)
        // Focus lands in the name field; on TV the IME opens on OK / Enter, not on focus.
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.enabled)
        ).assertIsNotFocused()
    }

    @Test
    fun tabbingBackToAnOpenEditorLeavesFocusOnTheTopBar() {
        val state = showHub(TvTimerPage.Edit(0))

        composeRule.onNodeWithTag("hub_tab_timers").requestFocus()
        composeRule.waitForIdle()
        val timersTab = composeRule.onNodeWithTag("hub_tab_timers")
        timersTab.performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()
        assertEquals(HubSection.Live(null), state.selected)

        composeRule.onNodeWithTag("hub_tab_timers")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.waitForIdle()

        assertEquals(HubSection.Timers, state.selected)
        // The editor came back with the section; it must not pull focus out of the bar.
        composeRule.onNodeWithTag("hub_tab_timers").assertIsFocused()
        composeRule.onNodeWithContentDescription("Title").assertIsNotFocused()

        // Down is the user's decision to enter the section.
        composeRule.onNodeWithTag("hub_tab_timers")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Title").assertIsFocused()
    }

    private fun showHub(initialPage: TvTimerPage): HubState {
        val state = HubState(initialPage)
        composeRule.setContent {
            state.inputModeManager = LocalInputModeManager.current
            ComposeTvHubChrome(
                section = state.selected,
                onOpenTab = {
                    state.selected =
                        if (it == HubTab.Timers) HubSection.Timers else HubSection.Live(null)
                },
                onOpenSettings = { state.selected = HubSection.Settings },
                onDrawerItemSelected = { state.selected = HubSection.Live(it) },
                settingsItems = emptyList(),
                onSettingsClick = {},
                bouquetRows = listOf(bouquet),
                timerContent = {
                    TvTimerHostContent(
                        uiState = TvTimerHostUiState(
                            page = state.page,
                            editorTimer = if (state.page is TvTimerPage.List) null else timers[0],
                            timers = timers,
                            emptyMessage = null
                        ),
                        onAdd = {},
                        onEdit = { state.page = TvTimerPage.Edit(it) },
                        onToggleEnabled = {},
                        onDelete = {},
                        onShowList = { state.page = TvTimerPage.List }
                    ) { timer, isCreate ->
                        DreamDroidTvTheme(fillBackground = false) {
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
                }
            )
        }

        // Switches and other clickables take focus only outside touch mode, as on a TV
        // driven by the remote.
        composeRule.runOnIdle { state.inputModeManager.requestInputMode(InputMode.Keyboard) }
        composeRule.runOnIdle {
            assertEquals(InputMode.Keyboard, state.inputModeManager.inputMode)
        }
        return state
    }
}
