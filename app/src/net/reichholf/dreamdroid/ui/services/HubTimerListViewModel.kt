package net.reichholf.dreamdroid.ui.services

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
import net.reichholf.dreamdroid.data.TimerListResult
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Timer
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * The hub Timers tab. [emptyMessage] replaces the list while it is empty: loading, the
 * load error, or "no items". [cleaning] is true while the receiver cleans up its list.
 */
data class HubTimerListUiState(
    val timers: List<Timer> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null,
    val cleaning: Boolean = false,
    val mutationsBlocked: Boolean = false,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(R.string.timer)
}

/**
 * Timer list of the hub, scoped to the hub back-stack entry. The same remount epoch does
 * not load again, so returning to the tab keeps the list.
 */
@HiltViewModel
class HubTimerListViewModel @Inject constructor(
    private val timers: TimerRepository,
    sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HubTimerListUiState(mutationsBlocked = sessions.status.value.blocksMutations)
    )
    val uiState: StateFlow<HubTimerListUiState> = _uiState.asStateFlow()

    private var appliedEpoch: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update { it.copy(mutationsBlocked = status.blocksMutations) }
            }
        }
    }

    /**
     * Loads when [epoch] differs from the last one; the hub bumps it after a timer edit, and
     * then the snapshot is stale, so the receiver answers.
     */
    fun onRemount(epoch: Int) {
        val key = epoch.toString()
        if (!shouldLoadHubPage(appliedEpoch, key)) {
            return
        }
        val edited = appliedEpoch != null
        appliedEpoch = key
        reload(forceRefresh = edited)
    }

    /**
     * Loads the timers. Room paints first unless [forceRefresh]; see [TimerRepository.timers].
     */
    fun reload(forceRefresh: Boolean = false) {
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.timers.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            timers.timers(forceRefresh).collect { result ->
                _uiState.update {
                    when (result) {
                        is TimerListResult.Loaded -> it.copy(
                            timers = result.timers,
                            refreshing = false,
                            emptyMessage = if (result.timers.isEmpty()) {
                                UiText.Resource(R.string.no_list_item)
                            } else {
                                null
                            }
                        )

                        is TimerListResult.Failed -> it.copy(
                            timers = emptyList(),
                            refreshing = false,
                            emptyMessage = result.message
                        )
                    }
                }
            }
        }
    }

    /** Asks the receiver to drop finished timers, then reloads. */
    fun cleanup() {
        val state = _uiState.value
        if (state.cleaning || state.mutationsBlocked) {
            return
        }
        _uiState.update { it.copy(cleaning = true) }
        viewModelScope.launch {
            val response = timers.cleanup()
            _uiState.update { it.copy(cleaning = false, userMessage = response.userMessageText()) }
            reload(forceRefresh = true)
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
