package net.reichholf.dreamdroid.ui.timers

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerHelper
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.theme.DreamDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The timer form in a list-detail pane, with its own top app bar. */
class TimerEditPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = mutableListOf<String>()

    @Test
    fun newTimerSavesFromThePaneBarAndOffersNoDelete() {
        show(TimerEditUiState(timer = TIMER, isCreate = true, locations = LOCATIONS))

        composeRule.onNodeWithText(context.getString(R.string.new_timer)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.delete))
            .assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()

        assertEquals(listOf("save"), calls)
    }

    @Test
    fun deleteAsksFirst() {
        show(TimerEditUiState(timer = TIMER, isCreate = false, locations = LOCATIONS))

        // The bar of an existing timer reads "Timer", not "New Timer" (the form says "Timer" too).
        composeRule.onNodeWithText(context.getString(R.string.new_timer)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.delete)).performClick()
        assertEquals(emptyList<String>(), calls)
        composeRule.onNodeWithText(context.getString(R.string.delete_confirm)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.delete)).performClick()

        assertEquals(listOf("delete"), calls)
    }

    @Test
    fun closeDismissesThePane() {
        show(TimerEditUiState(timer = TIMER, isCreate = false, locations = LOCATIONS))

        composeRule.onNodeWithContentDescription(context.getString(R.string.close)).performClick()

        assertEquals(listOf("close"), calls)
    }

    @Test
    fun actionsWaitForARunningRequest() {
        show(
            TimerEditUiState(
                timer = TIMER,
                isCreate = false,
                locations = LOCATIONS,
                progress = UiText.Raw("Saving")
            )
        )

        composeRule.onNodeWithText(context.getString(R.string.save)).assertIsNotEnabled()
    }

    private fun show(state: TimerEditUiState) {
        composeRule.setContent {
            DreamDroidTheme {
                TimerEditPane(
                    uiState = state,
                    name = remember { TextFieldState("Sample") },
                    description = remember { TextFieldState("Desc") },
                    actions = object : TimerFormActions {},
                    onPickService = {},
                    onSave = { calls += "save" },
                    onDelete = { calls += "delete" },
                    onClose = { calls += "close" }
                )
            }
        }
    }

    private companion object {
        val LOCATIONS = listOf("/hdd/movie/", "/media/hdd/")
        val TIMER = TimerHelper.getInitialTimer().copy(
            name = "Sample",
            description = "Desc",
            serviceName = "Das Erste HD",
            reference = "1:0:1:6DCA:44D:1:C00000:0:0:0:",
            begin = "1893456000",
            end = "1893459600"
        )
    }
}
