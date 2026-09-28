package net.reichholf.dreamdroid.tv.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.timers.TimerFormViewModel

/**
 * The timer editor behind [TvTimerEditorHost]. It outlives a configuration change and the
 * in-host service pick; the host calls [release] when it leaves composition for any other
 * reason, so the next [bind] starts from the launch timer. Nothing is kept across process
 * death.
 */
@HiltViewModel
class TvTimerEditViewModel @Inject constructor(
    timers: TimerRepository,
    sessions: SessionConnectionHolder
) : TimerFormViewModel(timers, sessions, handle = null) {
    private var launch: Pair<Timer, Boolean>? = null

    /** Edits [timer], unless that editor is open already. */
    fun bind(timer: Timer, isCreate: Boolean) {
        if (launch == timer to isCreate) {
            return
        }
        launch = timer to isCreate
        load(timer, isCreate)
    }

    /** Ignores a stale host whose editor a newer [bind] has already replaced. */
    fun release(timer: Timer, isCreate: Boolean) {
        if (launch != timer to isCreate) {
            return
        }
        launch = null
        close()
    }
}
