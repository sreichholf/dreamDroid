package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.timers.TimerFormViewModel

/**
 * The timer editor behind [TvTimerEditorHost]. It outlives a configuration change and the
 * in-host service pick, and keeps its edits in the [SavedStateHandle] for process death:
 * binding the same launch timer again shows them. The host calls [release] when it leaves
 * composition for any other reason, so the next [bind] starts from the launch timer.
 */
@HiltViewModel
class TvTimerEditViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    timers: TimerRepository,
    private val profiles: ProfileRepository,
    sessions: SessionConnectionHolder
) : TimerFormViewModel(timers, sessions, handle) {
    private var launch: Pair<Timer, Boolean>? = handle.get<Timer>(KEY_LAUNCH_TIMER)?.let {
        it to (handle.get<Boolean>(KEY_LAUNCH_CREATE) ?: false)
    }

    /** The ViewModel key of the service pick for the active profile. */
    fun servicePickKey(): String = tvTimerServicePickKey(profiles.current.value?.id)

    /** Edits [timer], unless that editor is open already or saved from an earlier process. */
    fun bind(timer: Timer, isCreate: Boolean) {
        val next = timer to isCreate
        if (launch == next) {
            if (uiState.value.timer == null && !restore()) {
                load(timer, isCreate)
            }
            return
        }
        launch = next
        handle[KEY_LAUNCH_TIMER] = timer
        handle[KEY_LAUNCH_CREATE] = isCreate
        load(timer, isCreate)
    }

    /** Ignores a stale host whose editor a newer [bind] has already replaced. */
    fun release(timer: Timer, isCreate: Boolean) {
        if (launch != timer to isCreate) {
            return
        }
        launch = null
        handle.remove<Timer>(KEY_LAUNCH_TIMER)
        handle.remove<Boolean>(KEY_LAUNCH_CREATE)
        close()
    }

    private companion object {
        const val KEY_LAUNCH_TIMER = "tv_timer_edit_launch_timer"
        const val KEY_LAUNCH_CREATE = "tv_timer_edit_launch_create"
    }
}
