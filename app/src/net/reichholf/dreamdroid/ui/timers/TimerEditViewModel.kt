package net.reichholf.dreamdroid.ui.timers

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.util.ArrayList
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.helpers.getSerializableCompat
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle

internal enum class TimerEditBind {
    Keep,
    RestoreSaved,
    LoadLaunch
}

/**
 * First bind restores a saved session when its route tag matches.
 * A later tag or remount epoch loads the launch timer instead.
 * Epoch mismatch on the first bind still restores: the host epoch restarts at 0.
 */
internal fun timerEditBind(
    hasBound: Boolean,
    boundTag: String,
    boundEpoch: Int,
    routeTag: String,
    remountEpoch: Int,
    savedTag: String?
): TimerEditBind {
    if (hasBound && boundTag == routeTag && boundEpoch == remountEpoch) {
        return TimerEditBind.Keep
    }
    if (!hasBound && savedTag == routeTag) {
        return TimerEditBind.RestoreSaved
    }
    return TimerEditBind.LoadLaunch
}

/**
 * Owns [TimerEditSession] for [TimerEditDestination].
 * Prefetch and save/delete run on [viewModelScope].
 * The snapshot uses [TimerEditSession.writeTo] / [TimerEditSession.fromSavedState] keys.
 * The composable registers the menu and sets [TimerEditSession.context] and
 * [TimerEditSession.handle].
 */
class TimerEditViewModel(application: Application, private val savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    var session by mutableStateOf<TimerEditSession?>(null)
        private set

    private var hasBound: Boolean = false
    private var boundTag: String = ""
    private var boundEpoch: Int = 0

    fun start(handle: PhoneNavHandle) {
        val tag = handle.timerEditRouteTag()
        val remount = handle.timerEditRemountEpoch
        val saved = if (hasBound) null else readSession()
        when (
            timerEditBind(
                hasBound = hasBound,
                boundTag = boundTag,
                boundEpoch = boundEpoch,
                routeTag = tag,
                remountEpoch = remount,
                savedTag = saved?.routeTag
            )
        ) {
            TimerEditBind.Keep -> {
                attachScope()
                return
            }

            TimerEditBind.RestoreSaved -> {
                bindSession(checkNotNull(saved).withRouteEpoch(remount), tag, remount)
            }

            TimerEditBind.LoadLaunch -> {
                bindSession(
                    TimerEditSession.fromArgs(handle.timerEditLeafArguments(), tag, remount),
                    tag,
                    remount
                )
            }
        }
    }

    /** Copies typed fields into the timer, then writes the session keys. */
    fun persist() {
        val current = session
        if (current == null || !hasBound) {
            return
        }
        current.flushFormIntoTimer()
        val bundle = Bundle()
        current.writeTo(bundle)
        putOrRemove(TimerEditSession.STATE_TAG, bundle.getString(TimerEditSession.STATE_TAG))
        putOrRemove(
            TimerEditSession.STATE_REMOUNT,
            bundle.getInt(TimerEditSession.STATE_REMOUNT)
        )
        putOrRemove(
            TimerEditSession.STATE_TIMER,
            bundle.getSerializableCompat<Timer>(TimerEditSession.STATE_TIMER)
        )
        putOrRemove(
            TimerEditSession.STATE_TIMER_OLD,
            bundle.getSerializableCompat<Timer>(TimerEditSession.STATE_TIMER_OLD)
        )
        putOrRemove(
            TimerEditSession.STATE_TAGS,
            bundle.getStringArrayList(TimerEditSession.STATE_TAGS)
        )
        putOrRemove(
            TimerEditSession.STATE_CREATE,
            bundle.getBoolean(TimerEditSession.STATE_CREATE)
        )
        putOrRemove(
            TimerEditSession.STATE_CHECKED,
            bundle.getBooleanArray(TimerEditSession.STATE_CHECKED)
        )
    }

    /**
     * Pause/dispose flush. Skips when [start] has already moved on to another
     * tag or epoch, so an older composition cannot overwrite the new snapshot.
     */
    fun persistIfBound(tag: String, epoch: Int) {
        if (hasBound && boundTag == tag && boundEpoch == epoch) {
            persist()
        }
    }

    private fun bindSession(next: TimerEditSession, tag: String, remount: Int) {
        session?.let { previous ->
            previous.cancelWork()
            previous.onWorkingCopyChanged = null
        }
        session = next
        boundTag = tag
        boundEpoch = remount
        hasBound = true
        attachScope()
        persist()
    }

    private fun attachScope() {
        val current = session ?: return
        current.workScope = viewModelScope
        current.onWorkingCopyChanged = { persist() }
    }

    private fun putOrRemove(key: String, value: Any?) {
        if (value == null) {
            savedStateHandle.remove<Any>(key)
        } else {
            savedStateHandle[key] = value
        }
    }

    private fun readSession(): TimerEditSession? {
        val bundle = Bundle()
        savedStateHandle.get<String>(TimerEditSession.STATE_TAG)?.let { tag ->
            bundle.putString(TimerEditSession.STATE_TAG, tag)
        }
        if (savedStateHandle.contains(TimerEditSession.STATE_REMOUNT)) {
            bundle.putInt(
                TimerEditSession.STATE_REMOUNT,
                savedStateHandle.get<Int>(TimerEditSession.STATE_REMOUNT) ?: 0
            )
        }
        savedStateHandle.get<Timer>(TimerEditSession.STATE_TIMER)?.let { timer ->
            bundle.putSerializable(TimerEditSession.STATE_TIMER, timer)
        }
        savedStateHandle.get<Timer>(TimerEditSession.STATE_TIMER_OLD)?.let { timer ->
            bundle.putSerializable(TimerEditSession.STATE_TIMER_OLD, timer)
        }
        val tags = savedStateHandle.get<ArrayList<*>>(TimerEditSession.STATE_TAGS)
        if (tags != null) {
            val copy = ArrayList<String>(tags.size)
            for (item in tags) {
                if (item is String) {
                    copy.add(item)
                }
            }
            bundle.putStringArrayList(TimerEditSession.STATE_TAGS, copy)
        }
        if (savedStateHandle.contains(TimerEditSession.STATE_CREATE)) {
            bundle.putBoolean(
                TimerEditSession.STATE_CREATE,
                savedStateHandle.get<Boolean>(TimerEditSession.STATE_CREATE) == true
            )
        }
        savedStateHandle.get<BooleanArray>(TimerEditSession.STATE_CHECKED)?.let { checked ->
            bundle.putBooleanArray(TimerEditSession.STATE_CHECKED, checked)
        }
        return TimerEditSession.fromSavedState(bundle)
    }
}
