package net.reichholf.dreamdroid.ui.timers

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * The timer form in the detail pane beside the hub's timer list, on windows that fit two panes.
 * [open] starts editing; the working copy survives the service pick and process death in the
 * [SavedStateHandle], as on the [net.reichholf.dreamdroid.ui.nav.TimerEdit] route.
 */
@HiltViewModel
class TimerPaneViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    timers: TimerRepository,
    sessions: SessionConnectionHolder
) : TimerFormViewModel(timers, sessions, handle) {
    private val _opened = MutableStateFlow(handle.get<Int>(KEY_OPENED))

    /** Counts [open] calls while a form is shown, null while none is: the pane's identity. */
    val opened: StateFlow<Int?> = _opened.asStateFlow()

    init {
        if (_opened.value != null && !restore()) {
            setOpened(null)
        }
    }

    fun open(timer: Timer, isCreate: Boolean) {
        load(timer, isCreate)
        setOpened((_opened.value ?: 0) + 1)
    }

    fun dismiss() {
        close()
        setOpened(null)
    }

    private fun setOpened(value: Int?) {
        _opened.value = value
        handle[KEY_OPENED] = value
    }

    private companion object {
        const val KEY_OPENED = "timer_pane_opened"
    }
}
