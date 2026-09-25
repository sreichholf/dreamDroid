package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer as TypedTimer
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler
import net.reichholf.dreamdroid.ui.dialogs.IndeterminateProgressState
import net.reichholf.dreamdroid.ui.services.TimerListItem
import net.reichholf.dreamdroid.ui.services.timerListItemsFrom

/**
 * TV hub timer list and list / add / edit [page] for [TvTimerHost]. The activity scope
 * keeps [page] and the loaded list across a configuration change. [reload] still runs
 * on each entry. Process death may drop [page]; it has no SavedStateHandle key.
 */
class TvTimerHostViewModel(application: Application) : AndroidViewModel(application) {
    internal var page by mutableStateOf<TvTimerPage>(TvTimerPage.List)
        private set

    /**
     * Timer passed into the add/edit editor. Held here so a configuration change
     * does not call [Timer.getInitialTimer] again (that stamps a new begin time
     * and the editor treats it as a different draft).
     */
    internal var editorTimer by mutableStateOf<TypedTimer?>(null)
        private set

    var timers by mutableStateOf<List<TypedTimer>>(emptyList())
        private set

    var items by mutableStateOf<List<TimerListItem>>(emptyList())
        private set

    var emptyMessage by mutableStateOf<String?>(application.getString(R.string.loading))
        private set

    var progress by mutableStateOf<IndeterminateProgressState?>(null)
        private set

    internal var loadPaint: suspend (Context) -> TvTimerLoadPaint = ::tvTimerLoadPaint

    private var loadJob: Job? = null
    private var mutateJob: Job? = null
    private var profileId: Int? = null

    fun showList() {
        page = TvTimerPage.List
        editorTimer = null
    }

    fun showAdd() {
        if (page is TvTimerPage.Add && editorTimer != null) {
            return
        }
        editorTimer = Timer.getInitialTimer()
        page = TvTimerPage.Add
    }

    fun showEdit(index: Int) {
        val timer = timers.getOrNull(index)
        if (timer == null) {
            showList()
            return
        }
        editorTimer = timer
        page = TvTimerPage.Edit(index)
    }

    fun reload() {
        val app = getApplication<Application>()
        val currentProfileId = DreamDroid.currentProfileOrNull()?.id
        // The activity outlives a profile switch in TvProfilesHost.
        if (currentProfileId != profileId) {
            profileId = currentProfileId
            timers = emptyList()
            items = emptyList()
        }
        if (timers.isEmpty()) {
            emptyMessage = app.getString(R.string.loading)
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            applyPaint(loadPaint(app))
        }
    }

    fun toggleEnabled(index: Int) {
        val timer = timers.getOrNull(index) ?: return
        val timerNew = timer.copy(disabled = tvTimerToggledDisabled(timer.disabled))
        mutate(R.string.saving, TimerChangeRequestHandler(), Timer.getSaveParams(timerNew, timer))
    }

    fun deleteTimer(index: Int) {
        val timer = timers.getOrNull(index) ?: return
        mutate(R.string.deleting, TimerDeleteRequestHandler(), Timer.getDeleteParams(timer))
    }

    private fun mutate(
        messageRes: Int,
        requestHandler: SimpleResultRequestHandler,
        params: List<NameValuePair>
    ) {
        if (progress != null) {
            return
        }
        progress = IndeterminateProgressState(
            message = getApplication<Application>().getString(messageRes)
        )
        mutateJob?.cancel()
        mutateJob =
            viewModelScope.launchSimpleResultLoad(requestHandler, params) { _, result, error ->
                onSimpleResult(result, error)
            }
    }

    private fun onSimpleResult(result: SimpleResult, error: EnigmaHttpError?) {
        val app = getApplication<Application>()
        progress = null
        var toastText = app.getText(R.string.get_content_error).toString()
        val stateText = result.stateText
        when {
            !stateText.isNullOrEmpty() -> toastText = stateText
            error != null -> toastText = error.resolve(app).orEmpty()
        }
        Toast.makeText(app, toastText, Toast.LENGTH_LONG).show()
        reload()
    }

    private fun applyPaint(paint: TvTimerLoadPaint) {
        val app = getApplication<Application>()
        if (paint.success) {
            timers = paint.timers
            items = timerListItemsFrom(app, paint.timers)
            emptyMessage = if (paint.timers.isEmpty()) {
                app.getString(R.string.no_list_item)
            } else {
                null
            }
        } else {
            timers = emptyList()
            items = emptyList()
            emptyMessage = paint.errorText
        }
    }
}
