package net.reichholf.dreamdroid.ui.dialogs

import androidx.compose.foundation.text.input.InputTransformation
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer
import net.reichholf.dreamdroid.ui.text.SavedTextField

data class SleepTimerUiState(
    val minutes: Int = SleepTimerViewModel.DEFAULT_MINUTES,
    val enabled: Boolean = false,
    val action: String = SleepTimer.ACTION_STANDBY
)

/**
 * The sleep-timer form. It starts from the box's timer in the
 * [net.reichholf.dreamdroid.ui.nav.SleepTimerRoute] arguments and keeps edits across
 * rotation and process death. The shell writes the timer.
 */
@HiltViewModel
class SleepTimerViewModel @Inject constructor(private val handle: SavedStateHandle) :
    ViewModel() {
    private val _uiState = MutableStateFlow(
        SleepTimerUiState(
            minutes = clampMinutes(
                handle[KEY_MINUTES] ?: handle[ROUTE_MINUTES] ?: DEFAULT_MINUTES
            ),
            enabled = handle[KEY_ENABLED] ?: handle[ROUTE_ENABLED] ?: false,
            action = knownAction(handle[KEY_ACTION] ?: handle[ROUTE_ACTION])
        )
    )
    val uiState: StateFlow<SleepTimerUiState> = _uiState.asStateFlow()

    /** The typed minutes; the slider and the +/- buttons write through [SavedTextField.set]. */
    val minutesText = SavedTextField(
        viewModelScope,
        handle,
        KEY_MINUTES_TEXT,
        initial = _uiState.value.minutes.toString()
    ) { raw -> update { it.copy(minutes = clampMinutes(raw.toIntOrNull() ?: 0)) } }

    fun onMinutesChanged(minutes: Int) {
        val clamped = clampMinutes(minutes)
        update { it.copy(minutes = clamped) }
        val text = clamped.toString()
        if (minutesText.text != text) {
            minutesText.set(text)
        }
    }

    fun adjustMinutes(delta: Int) {
        onMinutesChanged(uiState.value.minutes + delta)
    }

    fun onEnabledChanged(enabled: Boolean) {
        update { it.copy(enabled = enabled) }
    }

    fun onActionSelected(action: String) {
        update { it.copy(action = knownAction(action)) }
    }

    private fun update(transform: (SleepTimerUiState) -> SleepTimerUiState) {
        _uiState.update(transform)
        val state = _uiState.value
        handle[KEY_MINUTES] = state.minutes
        handle[KEY_ENABLED] = state.enabled
        handle[KEY_ACTION] = state.action
    }

    companion object {
        const val DEFAULT_MINUTES = 90
        private const val MAX_MINUTES = 999

        // Argument names of SleepTimerRoute.
        private const val ROUTE_MINUTES = "minutes"
        private const val ROUTE_ENABLED = "enabled"
        private const val ROUTE_ACTION = "action"

        private const val KEY_MINUTES = "sleep_timer_minutes"
        private const val KEY_MINUTES_TEXT = "sleep_timer_minutes_text"
        private const val KEY_ENABLED = "sleep_timer_enabled"
        private const val KEY_ACTION = "sleep_timer_action"

        /** At most three digits: the receiver takes a timer of up to [MAX_MINUTES] minutes. */
        val MinutesInput = InputTransformation {
            if (length > 3 || !asCharSequence().all { it.isDigit() }) {
                revertAllChanges()
            }
        }

        private fun clampMinutes(minutes: Int): Int = minutes.coerceIn(0, MAX_MINUTES)

        /** Shutdown stays shutdown; anything else, including no action, is standby. */
        private fun knownAction(action: String?): String =
            if (action == SleepTimer.ACTION_SHUTDOWN) {
                SleepTimer.ACTION_SHUTDOWN
            } else {
                SleepTimer.ACTION_STANDBY
            }
    }
}
