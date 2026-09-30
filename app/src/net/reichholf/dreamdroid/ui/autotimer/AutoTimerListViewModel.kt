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
import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerEntry
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** What the list area shows. */
sealed interface AutoTimerListContent {
    data object Loading : AutoTimerListContent

    /** The receiver does not list the AutoTimer plugin. */
    data object PluginMissing : AutoTimerListContent

    data class Failed(val message: UiText) : AutoTimerListContent

    data class Ready(val entries: List<AutoTimerEntry>) : AutoTimerListContent
}

/**
 * The receiver's AutoTimers. [blocked] mirrors the session's `blocksMutations`; [refreshing]
 * is true while a shown list loads again.
 */
data class AutoTimerListUiState(
    val content: AutoTimerListContent = AutoTimerListContent.Loading,
    val refreshing: Boolean = false,
    val blocked: Boolean = false
) {
    val title: UiText
        get() = UiText.Resource(R.string.autotimer)
}

/**
 * The AutoTimers of the active profile's receiver, read from the box on every load. A profile
 * change lists the new receiver's AutoTimers; a failed list loads again once the session
 * takes requests.
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
                _uiState.update { it.copy(content = AutoTimerListContent.Loading) }
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
        loadJob?.cancel()
        _uiState.update {
            if (it.content is AutoTimerListContent.Ready) {
                it.copy(refreshing = true)
            } else {
                it.copy(content = AutoTimerListContent.Loading)
            }
        }
        loadJob = viewModelScope.launch {
            val content = when (val load = autoTimers.list()) {
                is AutoTimerLoad.Ready -> AutoTimerListContent.Ready(load.entries)
                AutoTimerLoad.PluginMissing -> AutoTimerListContent.PluginMissing
                is AutoTimerLoad.Failed -> AutoTimerListContent.Failed(load.message)
            }
            _uiState.update { it.copy(content = content, refreshing = false) }
        }
    }
}
