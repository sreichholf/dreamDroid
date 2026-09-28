package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.TimerListResult
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.helpers.enigma2.Timer as TimerRequests
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

sealed interface TvTimerPage {
    data object List : TvTimerPage
    data object Add : TvTimerPage
    data class Edit(val index: Int) : TvTimerPage
}

/**
 * The TV Timers header. [editorTimer] is the timer the Add or Edit [page] edits; it is kept
 * so a configuration change does not stamp a new begin time on a draft. [emptyMessage]
 * replaces an empty list; [progress] names the running mutation.
 */
data class TvTimerHostUiState(
    val page: TvTimerPage = TvTimerPage.List,
    val editorTimer: Timer? = null,
    val timers: List<Timer> = emptyList(),
    val emptyMessage: UiText? = UiText.Resource(R.string.loading),
    val progress: UiText? = null,
    val mutationsBlocked: Boolean = false,
    val userMessage: UiText? = null
)

/**
 * List, add, and edit for [TvTimerHost]. The scope keeps the page and the loaded list
 * across a configuration change; [reload] still runs on each entry. When the session is
 * not Online, the Room snapshot paints without asking the receiver.
 */
@HiltViewModel
class TvTimerHostViewModel @Inject constructor(
    private val timers: TimerRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TvTimerHostUiState(mutationsBlocked = sessions.status.value.blocksMutations)
    )
    val uiState: StateFlow<TvTimerHostUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var profileId: Int? = null

    init {
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update { it.copy(mutationsBlocked = status.blocksMutations) }
            }
        }
    }

    fun showList() {
        _uiState.update { it.copy(page = TvTimerPage.List, editorTimer = null) }
    }

    /** Opens add with a new timer; keeps the draft when add is already open. */
    fun showAdd() {
        val state = _uiState.value
        if (state.page is TvTimerPage.Add && state.editorTimer != null) {
            return
        }
        _uiState.update {
            it.copy(page = TvTimerPage.Add, editorTimer = TimerRequests.getInitialTimer())
        }
    }

    fun showEdit(index: Int) {
        val timer = _uiState.value.timers.getOrNull(index)
        if (timer == null) {
            showList()
            return
        }
        _uiState.update { it.copy(page = TvTimerPage.Edit(index), editorTimer = timer) }
    }

    fun reload() {
        val currentProfileId = profiles.current.value?.id
        // The activity outlives a profile switch in TvProfilesHost.
        if (currentProfileId != profileId) {
            profileId = currentProfileId
            _uiState.update { it.copy(timers = emptyList()) }
        }
        _uiState.update {
            if (it.timers.isEmpty()) {
                it.copy(
                    emptyMessage = UiText.Resource(R.string.loading)
                )
            } else {
                it
            }
        }
        val preferSnapshot = sessions.status.value.session != ConnectionStatus.Session.Online
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = timers.timers(preferSnapshot)
            _uiState.update {
                when (result) {
                    is TimerListResult.Loaded -> it.copy(
                        timers = result.timers,
                        emptyMessage = if (result.timers.isEmpty()) {
                            UiText.Resource(R.string.no_list_item)
                        } else {
                            null
                        }
                    )

                    is TimerListResult.Failed ->
                        it.copy(timers = emptyList(), emptyMessage = result.message)
                }
            }
        }
    }

    fun toggleEnabled(index: Int) {
        val timer = _uiState.value.timers.getOrNull(index) ?: return
        mutate(R.string.saving) { timers.toggleEnabled(timer) }
    }

    fun deleteTimer(index: Int) {
        val timer = _uiState.value.timers.getOrNull(index) ?: return
        mutate(R.string.deleting) { timers.delete(timer) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun mutate(progress: Int, call: suspend () -> EnigmaResponse<SimpleResult>) {
        val state = _uiState.value
        if (state.progress != null || state.mutationsBlocked) {
            return
        }
        _uiState.update { it.copy(progress = UiText.Resource(progress)) }
        viewModelScope.launch {
            val response = call()
            _uiState.update { it.copy(progress = null, userMessage = response.userMessageText()) }
            reload()
        }
    }
}
