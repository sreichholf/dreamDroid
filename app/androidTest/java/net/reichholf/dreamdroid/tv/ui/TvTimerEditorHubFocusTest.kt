package net.reichholf.dreamdroid.tv.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.timers.TimerEditUiState
import net.reichholf.dreamdroid.ui.timers.TimerFormActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Opening the hub Timers editor must keep the Timers header selected. The drawer selects
 * a header when its item gains focus, so the editor has to take focus instead of letting
 * it fall back to the first drawer item when the edit button leaves the composition.
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

        composeRule.setContent {
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

        val edit = composeRule.onNodeWithTag("tv_timers_edit_0")
        edit.requestFocus()
        composeRule.waitForIdle()
        edit.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertEquals("header selection changed", TvComposeHubHost.HEADER_TIMERS_ID, selected)
        composeRule.onNodeWithContentDescription("Title").assertIsDisplayed()
    }
}
