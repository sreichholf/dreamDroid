package net.reichholf.dreamdroid.ui.epg

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.PluginPresence
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.enigma.withReadableTimes
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * The EPG detail sheet: the [event] shown, a timer being [saving], and its result.
 * [timerWritesBlocked] follows the session.
 */
data class EpgEventDetailUiState(
    val event: Event? = null,
    val saving: Boolean = false,
    val userMessage: UiText? = null,
    val timerWritesBlocked: Boolean = false,
    /** The receiver has the AutoTimer plugin, so the sheet offers Record series. */
    val autoTimerAvailable: Boolean = false
)

/**
 * The EPG detail sheet of list EPG, MultiEPG, and hub destinations. The shown event lives
 * in the [SavedStateHandle]. Setting a timer runs on [viewModelScope], so the result
 * arrives even when the sheet closed meanwhile.
 */
@HiltViewModel
class EpgEventDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val timers: TimerRepository,
    autoTimers: AutoTimerRepository,
    sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        EpgEventDetailUiState(event = savedStateHandle.get<Event>(KEY_EVENT))
    )
    val uiState: StateFlow<EpgEventDetailUiState> =
        combine(_uiState, sessions.status, autoTimers.presence) { state, connection, presence ->
            state.copy(
                timerWritesBlocked = connection.blocksMutations,
                autoTimerAvailable = presence == PluginPresence.Present
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            _uiState.value.copy(timerWritesBlocked = sessions.status.value.blocksMutations)
        )

    fun showDetail(event: Event) {
        val display = event.withReadableTimes()
        savedStateHandle[KEY_EVENT] = display
        _uiState.update { it.copy(event = display) }
    }

    fun dismissDetail() {
        savedStateHandle.remove<Event>(KEY_EVENT)
        _uiState.update { it.copy(event = null) }
    }

    /** Adds a timer for [event] by its event id. Ignored while one is being saved. */
    fun setTimer(event: Event) {
        if (_uiState.value.saving) {
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val response = timers.addByEvent(event)
            _uiState.update { it.copy(saving = false, userMessage = response.userMessageText()) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private companion object {
        const val KEY_EVENT = "epg_detail_event"
    }
}
