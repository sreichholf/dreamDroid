package net.reichholf.dreamdroid.ui.epg

import androidx.lifecycle.SavedStateHandle
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
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.EventListLoad
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.nav.ServiceEpg
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** The schedule of one service. An empty [serviceRef] means the route had none. */
data class ServiceEpgUiState(
    val serviceRef: String = "",
    val serviceName: String = "",
    val events: List<Event> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null
) {
    val title: UiText
        get() = if (refreshing) {
            UiText.Resource(R.string.loading)
        } else {
            UiText.Resource(
                R.string.title_with_status,
                listOf(UiText.Resource(R.string.epg), UiText.Raw(serviceName))
            )
        }
}

/**
 * Service EPG of one back-stack entry; the service comes from the [ServiceEpg] route. It
 * loads when created and when the connection session changes, not when the destination is
 * shown again, so opening an event and popping back keeps the list.
 */
@HiltViewModel
class ServiceEpgViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val epg: EpgRepository,
    sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ServiceEpgUiState(
            serviceRef = savedStateHandle.get<String>(ServiceEpg::serviceRef.name).orEmpty(),
            serviceName = savedStateHandle.get<String>(ServiceEpg::serviceName.name).orEmpty()
        )
    )
    val uiState: StateFlow<ServiceEpgUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        if (_uiState.value.serviceRef.isNotEmpty()) {
            viewModelScope.launch {
                sessions.status.map { it.session }.distinctUntilChanged().collect { reload() }
            }
        }
    }

    fun reload(forceRefresh: Boolean = false) {
        val serviceRef = _uiState.value.serviceRef
        if (serviceRef.isEmpty()) {
            return
        }
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.events.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            epg.serviceEvents(serviceRef, forceRefresh).collect { load ->
                _uiState.update { it.applied(load) }
            }
        }
    }
}

private fun ServiceEpgUiState.applied(load: EventListLoad): ServiceEpgUiState = when (load) {
    is EventListLoad.Events -> copy(
        refreshing = false,
        events = load.events,
        emptyMessage = if (load.events.isEmpty()) UiText.Resource(R.string.no_list_item) else null
    )

    is EventListLoad.Failed -> copy(
        refreshing = false,
        events = emptyList(),
        emptyMessage = load.error.contentErrorText()
    )
}
