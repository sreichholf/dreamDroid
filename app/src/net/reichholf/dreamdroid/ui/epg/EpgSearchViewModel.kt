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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

/** EPG search results for the route's [query]. */
data class EpgSearchUiState(
    val query: String = "",
    val events: List<Event> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null
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
    private val epg: EpgRepository
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

    fun reload() {
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
            val response = epg.search(query)
            val events = response.value
            _uiState.update {
                when {
                    events == null -> it.copy(
                        refreshing = false,
                        events = emptyList(),
                        emptyMessage = response.error.contentErrorText()
                    )

                    else -> it.copy(
                        refreshing = false,
                        events = events,
                        emptyMessage = if (events.isEmpty()) {
                            UiText.Resource(R.string.no_list_item)
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    private companion object {
        const val KEY_DRAFT = "epg_search_draft"
    }
}
