package net.reichholf.dreamdroid.ui.autotimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AutoTimerLoad
import net.reichholf.dreamdroid.data.AutoTimerRepository
import net.reichholf.dreamdroid.data.AutoTimerWriteResult
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.ui.compose.RowMenuAction
import net.reichholf.dreamdroid.ui.compose.RowMenuState
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

enum class AutoTimerRowAction(override val label: Int) : RowMenuAction {
    Delete(R.string.delete)
}

/** What the list area shows. */
sealed interface AutoTimerListContent {
    data object Loading : AutoTimerListContent

    /** The receiver does not list the AutoTimer plugin. */
    data object PluginMissing : AutoTimerListContent

    data class Failed(val message: UiText) : AutoTimerListContent

    data class Ready(val entries: List<AutoTimerEntry>) : AutoTimerListContent
}

/**
 * The receiver's AutoTimers. [blocked] mirrors the session's `blocksMutations`; [pending] is
 * true while a write (and the list after it) runs; [refreshing] while a shown list loads
 * again. [deleting] is the entry the delete dialog asks about.
 */
data class AutoTimerListUiState(
    val content: AutoTimerListContent = AutoTimerListContent.Loading,
    val refreshing: Boolean = false,
    val blocked: Boolean = false,
    val pending: Boolean = false,
    val menu: RowMenuState<AutoTimerRowAction>? = null,
    val deleting: AutoTimerEntry? = null,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.autotimer)

    /** Whether the list takes a write now. */
    val editable: Boolean
        get() = content is AutoTimerListContent.Ready && !blocked && !pending && !refreshing
}

/**
 * The AutoTimers of the active profile's receiver, read from the box on every load. A profile
 * change lists the new receiver's AutoTimers; a failed list loads again once the session
 * takes requests. Every write lists the AutoTimers again, since the box may renumber them.
 */
@HiltViewModel
class AutoTimerListViewModel @Inject constructor(
    private val autoTimers: AutoTimerRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AutoTimerListUiState(blocked = sessions.status.value.blocksMutations)
    )
    val uiState: StateFlow<AutoTimerListUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            autoTimers.profileId.collect { id ->
                loadJob?.cancel()
                _uiState.update {
                    it.copy(
                        content = AutoTimerListContent.Loading,
                        refreshing = false,
                        pending = false,
                        menu = null,
                        deleting = null
                    )
                }
                if (id != null) {
                    reload()
                }
            }
        }
        viewModelScope.launch {
            sessions.status.map { it.blocksMutations }.distinctUntilChanged().collect { blocked ->
                _uiState.update { it.copy(blocked = blocked) }
                if (!blocked && _uiState.value.content is AutoTimerListContent.Failed) {
                    reload()
                }
            }
        }
    }

    fun reload() {
        if (_uiState.value.pending) {
            return
        }
        loadJob?.cancel()
        _uiState.update {
            if (it.content is AutoTimerListContent.Ready) {
                it.copy(refreshing = true)
            } else {
                it.copy(content = AutoTimerListContent.Loading)
            }
        }
        loadJob = viewModelScope.launch {
            val content = fetch()
            _uiState.update { it.copy(content = content, refreshing = false, menu = null) }
        }
    }

    /** Enables or pauses [entry]; the switch shows the new state while the box answers. */
    fun setEnabled(entry: AutoTimerEntry.Readable, enabled: Boolean) {
        if (entry.autoTimer.settings.enabled == enabled) {
            return
        }
        write(
            shown = { entries ->
                entries.map {
                    if (it == entry) {
                        AutoTimerEntry.Readable(
                            entry.autoTimer.copy(
                                settings = entry.autoTimer.settings.copy(enabled = enabled)
                            )
                        )
                    } else {
                        it
                    }
                }
            },
            reportDone = false
        ) { autoTimers.setEnabled(entry.autoTimer, enabled) }
    }

    fun onItemMenu(entry: AutoTimerEntry) {
        val state = _uiState.value
        if (state.content !is AutoTimerListContent.Ready || state.pending) {
            return
        }
        _uiState.update {
            it.copy(menu = RowMenuState(entry.id.value, AutoTimerRowAction.entries))
        }
    }

    fun onMenuDismiss() {
        _uiState.update { it.copy(menu = null) }
    }

    fun onMenuAction(entry: AutoTimerEntry, action: AutoTimerRowAction) {
        when (action) {
            AutoTimerRowAction.Delete -> if (_uiState.value.editable) {
                _uiState.update { it.copy(deleting = entry, menu = null) }
            }
        }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(deleting = null) }
    }

    fun confirmDelete() {
        val entry = _uiState.value.deleting ?: return
        dismissDelete()
        write(shown = { entries -> entries - entry }, reportDone = true) {
            autoTimers.remove(entry)
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /**
     * Runs [call] as the one pending write while the list shows [shown], then lists the
     * AutoTimers again. The box's reply is shown for a failure, and for success when
     * [reportDone].
     */
    private fun write(
        shown: (List<AutoTimerEntry>) -> List<AutoTimerEntry>,
        reportDone: Boolean,
        call: suspend () -> AutoTimerWriteResult
    ) {
        val state = _uiState.value
        val content = state.content
        if (!state.editable || content !is AutoTimerListContent.Ready) {
            return
        }
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                pending = true,
                menu = null,
                content = AutoTimerListContent.Ready(shown(content.entries))
            )
        }
        loadJob = viewModelScope.launch {
            val message = when (val result = call()) {
                is AutoTimerWriteResult.Done -> result.message.takeIf { reportDone }
                AutoTimerWriteResult.Conflict -> UiText.Resource(R.string.autotimer_changed)
                is AutoTimerWriteResult.Failed -> result.message
            }
            val listed = fetch()
            _uiState.update {
                it.copy(
                    pending = false,
                    content = listed,
                    userMessage = message ?: it.userMessage
                )
            }
        }
    }

    private suspend fun fetch(): AutoTimerListContent = when (val load = autoTimers.list()) {
        is AutoTimerLoad.Ready -> AutoTimerListContent.Ready(load.entries)
        AutoTimerLoad.PluginMissing -> AutoTimerListContent.PluginMissing
        is AutoTimerLoad.Failed -> AutoTimerListContent.Failed(load.message)
    }
}
