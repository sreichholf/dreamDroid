package net.reichholf.dreamdroid.ui.timers

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.ui.nav.TimerEdit
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * Create or edit the timer of the [TimerEdit] route. The working copy survives the
 * service pick and process death in the [SavedStateHandle].
 */
@HiltViewModel
class TimerEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    timers: TimerRepository,
    sessions: SessionConnectionHolder
) : TimerFormViewModel(timers, sessions, savedStateHandle) {
    init {
        if (!restore()) {
            val route = savedStateHandle.timerEditRoute()
            load(route.toTimer(), route.create)
        }
    }

    /** Deletes the timer on the receiver. Creating has nothing to delete. */
    fun delete() {
        val state = uiState.value
        val timer = state.timer ?: return
        if (state.isCreate) {
            return
        }
        val deleted = original ?: timer
        request(R.string.deleting) { timers.delete(deleted) }
    }
}

/**
 * The [TimerEdit] arguments, read by name. `toRoute()` decodes through `Bundle`, which
 * JVM tests only have as a stub.
 */
private fun SavedStateHandle.timerEditRoute(): TimerEdit {
    fun text(key: String): String = get<String>(key).orEmpty()
    return TimerEdit(
        create = get<Boolean>("create") ?: false,
        reference = text("reference"),
        serviceName = text("serviceName"),
        eit = text("eit"),
        name = text("name"),
        description = text("description"),
        descriptionExtended = text("descriptionExtended"),
        disabled = text("disabled"),
        begin = text("begin"),
        end = text("end"),
        duration = text("duration"),
        beginReadable = text("beginReadable"),
        endReadable = text("endReadable"),
        durationReadable = text("durationReadable"),
        startPrepare = text("startPrepare"),
        justPlay = text("justPlay"),
        afterEvent = text("afterEvent"),
        location = text("location"),
        tags = text("tags"),
        logEntries = text("logEntries"),
        fileName = text("fileName"),
        backOff = text("backOff"),
        nextActivation = text("nextActivation"),
        firstTryPrepare = text("firstTryPrepare"),
        state = text("state"),
        repeated = text("repeated"),
        dontSave = text("dontSave"),
        canceled = text("canceled"),
        toggleDisabled = text("toggleDisabled"),
        allowDuplicate = get<String>("allowDuplicate"),
        autoAdjust = get<String>("autoAdjust"),
        vpsMode = get<String>("vpsMode"),
        vpsTime = get<Long>("vpsTime") ?: -1
    )
}
