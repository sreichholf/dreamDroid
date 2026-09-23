package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.reichholf.dreamdroid.enigma.Timer

/**
 * Owns the [TvTimerEditWorkingCopy] for [TvTimerEditorHost]. The TV activities have no
 * NavHost, so this is scoped to the activity and outlives a configuration change.
 * The host calls [release] when it leaves composition for any other reason, so the
 * next open starts from the launch timer. No field was saved across process death
 * before this ViewModel, so it keeps no [androidx.lifecycle.SavedStateHandle] keys.
 */
class TvTimerEditViewModel(application: Application) : AndroidViewModel(application) {
    private var session: TvTimerEditWorkingCopy? = null

    internal fun bind(timer: Timer, isCreate: Boolean): TvTimerEditWorkingCopy {
        val current = session
        if (current != null && current.launchTimer == timer && current.isCreate == isCreate) {
            return current
        }
        current?.cancelWork()
        return TvTimerEditWorkingCopy(timer, isCreate, getApplication(), viewModelScope)
            .also { session = it }
    }

    /** Ignores a stale host whose session a newer [bind] has already replaced. */
    internal fun release(owned: TvTimerEditWorkingCopy) {
        if (session !== owned) {
            return
        }
        owned.cancelWork()
        session = null
    }
}
