package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.text.input.TextFieldState
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
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * EPG search results for the route's [query]. [cached] results come from the Room EPG cache
 * and only cover bouquets opened on this device.
 */
data class EpgSearchUiState(
    val query: String = "",
    val events: List<Event> = emptyList(),
    val cached: Boolean = false,
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null,
    val piconsEnabled: Boolean = false
) {
    val title: UiText
        get() = UiText.Resource(if (refreshing) R.string.loading else R.string.epg_search)
}

/**
 * EPG search list, the search field, and the load for [EpgSearchDestination]. The field's
 * draft survives process death. The search bar's expanded flag stays in the composable.
 */
@HiltViewModel
class EpgSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val epg: EpgRepository,
    settings: SettingsRepository
) : ViewModel() {
    private val hasSavedDraft = KEY_DRAFT in savedStateHandle
    private val searchField = SavedTextField(viewModelScope, savedStateHandle, KEY_DRAFT)

    /** The search field; the user edits it directly. */
    val queryState: TextFieldState
        get() = searchField.state

    private val _uiState = MutableStateFlow(EpgSearchUiState())
    val uiState: StateFlow<EpgSearchUiState> = _uiState.asStateFlow()

    private var boundEpoch: Int? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.picons }.distinctUntilChanged().collect { on ->
                _uiState.update { it.copy(piconsEnabled = on) }
            }
        }
    }

    /**
     * Applies the route [query] and [remountEpoch] and searches. The first call keeps a
     * restored draft. A later change of either resets the draft to [query]; the same
     * values again change nothing.
     */
    fun syncRoute(query: String, remountEpoch: Int) {
        val previousEpoch = boundEpoch
        if (previousEpoch != null && query == _uiState.value.query &&
            remountEpoch == previousEpoch
        ) {
            return
        }
        if (previousEpoch != null || !hasSavedDraft) {
            searchField.set(query)
        }
        boundEpoch = remountEpoch
        _uiState.update { EpgSearchUiState(query = query) }
        reload()
    }

    /**
     * Searches for the route query. [forceRefresh] asks the receiver before the cache, as
     * pull-to-refresh does. Stays refreshing until the last result arrived.
     */
    fun reload(forceRefresh: Boolean = false) {
        val query = _uiState.value.query
        loadJob?.cancel()
        loadJob = null
        if (query.isEmpty()) {
            _uiState.update { it.copy(refreshing = false) }
            return
        }
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.events.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
        loadJob = viewModelScope.launch {
            epg.search(query, forceRefresh).collect { load ->
                _uiState.update { it.applied(load) }
            }
            _uiState.update { it.copy(refreshing = false) }
        }
    }

    private companion object {
        const val KEY_DRAFT = "epg_search_draft"
    }
}

private fun EpgSearchUiState.applied(load: EventListLoad): EpgSearchUiState = when (load) {
    is EventListLoad.Events -> copy(
        events = load.events,
        cached = load.cached,
        emptyMessage = when {
            load.events.isNotEmpty() -> null
            load.cached -> UiText.Resource(R.string.epg_search_no_cached_match)
            else -> UiText.Resource(R.string.no_list_item)
        }
    )

    is EventListLoad.Failed -> copy(
        events = emptyList(),
        cached = false,
        emptyMessage = load.error.contentErrorText()
    )
}
