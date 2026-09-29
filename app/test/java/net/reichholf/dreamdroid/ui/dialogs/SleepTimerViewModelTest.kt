package net.reichholf.dreamdroid.ui.dialogs

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SleepTimerViewModelTest {
    @Test
    fun startsFromTheRouteArguments() {
        val viewModel = SleepTimerViewModel(route(45, true, SleepTimer.ACTION_SHUTDOWN))

        assertEquals(
            SleepTimerUiState(minutes = 45, enabled = true, action = SleepTimer.ACTION_SHUTDOWN),
            viewModel.uiState.value
        )
    }

    @Test
    fun anUnknownActionIsStandbyAndMinutesAreClamped() {
        val viewModel = SleepTimerViewModel(route(5000, false, ""))

        assertEquals(
            SleepTimerUiState(minutes = 999, enabled = false, action = SleepTimer.ACTION_STANDBY),
            viewModel.uiState.value
        )
    }

    @Test
    fun editsSurviveProcessDeathOverTheRouteArguments() {
        val handle = route(45, false, SleepTimer.ACTION_STANDBY)
        val first = SleepTimerViewModel(handle)
        first.adjustMinutes(1)
        first.onEnabledChanged(true)
        first.onActionSelected(SleepTimer.ACTION_SHUTDOWN)

        val restored = SleepTimerViewModel(handle)

        assertEquals(
            SleepTimerUiState(minutes = 46, enabled = true, action = SleepTimer.ACTION_SHUTDOWN),
            restored.uiState.value
        )
    }

    @Test
    fun typedMinutesKeepThreeDigits() {
        val viewModel = SleepTimerViewModel(route(45, false, SleepTimer.ACTION_STANDBY))

        viewModel.onMinutesTyped("12a34")
        assertEquals(123, viewModel.uiState.value.minutes)
        viewModel.onMinutesTyped("")
        assertEquals(0, viewModel.uiState.value.minutes)
    }

    private fun route(minutes: Int, enabled: Boolean, action: String) =
        SavedStateHandle(mapOf("minutes" to minutes, "enabled" to enabled, "action" to action))
}
