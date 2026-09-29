package net.reichholf.dreamdroid.ui.epg

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.text.SavedTextField
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * EPG search screen state. With [showRecent] the field holds too little to search and the
 * screen lists [recentSearches]; otherwise it lists [sections]. [cached] results come from
 * the Room EPG cache and only cover bouquets opened on this device. [retryable]: the
 * receiver failed and nothing is shown, so the empty state offers a retry.
 */
data class EpgSearchUiState(
    val recentSearches: List<String> = emptyList(),
    val showRecent: Boolean = true,
    val sections: List<EpgDaySection> = emptyList(),
    val cached: Boolean = false,
    val searching: Boolean = false,
    val emptyMessage: UiText? = null,
    val retryable: Boolean = false,
    val piconsEnabled: Boolean = false
) {
    val title: UiText
        get() = UiText.Resource(R.string.epg_search)
}

/**
 * EPG search as you type, for [EpgSearchDestination]. The field's draft survives process
 * death.
 *
 * Typing searches Room after [CACHE_SETTLE_MS] and the receiver only after the user paused
 * for [RECEIVER_SETTLE_MS] with at least [MIN_TYPED_LENGTH] characters. Receiver searches
 * run one at a time: while one runs, later queries wait and only the newest one is sent,
 * and [EpgRepository.receiverSearch] serialises them across screens, so a slow box never
 * serves two searches at once. Submitting, a recent search, the route query, and
 * pull-to-refresh search right away; submitting an answered query does not search again.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class EpgSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val epg: EpgRepository,
    settings: SettingsRepository
) : ViewModel() {
    private val hasSavedDraft = KEY_DRAFT in savedStateHandle
    private val searchField =
        SavedTextField(viewModelScope, savedStateHandle, KEY_DRAFT, onEdit = ::onTyped)

    /** The search field; the user edits it directly. */
    val queryState: TextFieldState
        get() = searchField.state

    private val _uiState = MutableStateFlow(EpgSearchUiState())
    val uiState: StateFlow<EpgSearchUiState> = _uiState.asStateFlow()

    private val requests = MutableStateFlow(SearchRequest("", immediate = false, nonce = 0))
    private val results = MutableStateFlow(SearchResults(requests.value))

    private var routeQuery: String? = null
    private var boundEpoch: Int? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.picons }.distinctUntilChanged().collect { on ->
                _uiState.update { it.copy(piconsEnabled = on) }
            }
        }
        viewModelScope.launch {
            epg.recentSearches().collect { recent ->
                _uiState.update { it.copy(recentSearches = recent) }
            }
        }
        viewModelScope.launch {
            results.collect { current -> _uiState.update { current.applyTo(it) } }
        }
        viewModelScope.launch {
            requests.debounce { if (it.immediate) 0L else CACHE_SETTLE_MS }
                .collectLatest(::searchCache)
        }
        viewModelScope.launch {
            requests.debounce { if (it.immediate) 0L else RECEIVER_SETTLE_MS }
                .filter { it.searchable }
                .conflate()
                .collect(::searchReceiver)
        }
    }

    /**
     * Applies the route [query] and [remountEpoch] and searches. The first call keeps a
     * restored draft. A later change of either resets the draft to [query]; the same
     * values again change nothing.
     */
    fun syncRoute(query: String, remountEpoch: Int) {
        val previousEpoch = boundEpoch
        if (previousEpoch != null && query == routeQuery && remountEpoch == previousEpoch) {
            return
        }
        if (previousEpoch != null || !hasSavedDraft) {
            searchField.set(query)
        }
        routeQuery = query
        boundEpoch = remountEpoch
        search(searchField.text, immediate = true)
    }

    /** The keyboard's search action: searches now and remembers the query. */
    fun submit() {
        val query = searchField.text.trim()
        if (query.isEmpty()) {
            return
        }
        rememberQuery(query)
        searchUnlessAnswered(query)
    }

    fun searchRecent(query: String) {
        searchField.set(query)
        rememberQuery(query)
        searchUnlessAnswered(query)
    }

    fun forgetRecent(query: String) {
        viewModelScope.launch { epg.forgetSearch(query) }
    }

    /** A result was opened: the query found something worth remembering. */
    fun onResultOpened() {
        val query = requests.value.query
        if (query.isNotEmpty()) {
            rememberQuery(query)
        }
    }

    /** Pull-to-refresh: searches the current query again right away. */
    fun reload() {
        search(requests.value.query, immediate = true)
    }

    /**
     * Searches [query] right away unless the receiver already answered it, is asking the box
     * for it now, or was skipped offline: pressing search on the shown results only closes
     * the keyboard, it does not ask the box again. A failed answer is searched again.
     */
    private fun searchUnlessAnswered(query: String) {
        val current = results.value
        val sameQuery = current.request.query == query && current.request.searchable
        val receiverDone = !current.receiverPending && current.live?.value != null
        val skippedOffline = !current.receiverPending && current.live == null
        val answered = sameQuery &&
            (current.receiverInFlight || receiverDone || skippedOffline)
        if (!answered) {
            search(query, immediate = true)
        }
    }

    private fun onTyped(text: String) {
        search(text, immediate = false)
    }

    private fun search(text: String, immediate: Boolean) {
        val query = text.trim()
        val previous = requests.value
        if (!immediate && query == previous.query) {
            return
        }
        val request = SearchRequest(query, immediate, previous.nonce + 1)
        // Results first: the pipelines drop work for a request the results do not show.
        results.value = SearchResults(request, cacheDone = !request.searchable)
        requests.value = request
    }

    private fun rememberQuery(query: String) {
        viewModelScope.launch { epg.rememberSearch(query) }
    }

    private suspend fun searchCache(request: SearchRequest) {
        if (!request.searchable) {
            return
        }
        val cached = epg.cachedSearch(request.query)
        val skipReceiver = epg.skipsReceiverSearch()
        results.update {
            if (it.request != request) {
                it
            } else {
                it.copy(
                    cached = cached,
                    cacheDone = true,
                    receiverPending = it.receiverPending && !skipReceiver
                )
            }
        }
    }

    private suspend fun searchReceiver(request: SearchRequest) {
        if (results.value.request.query != request.query) {
            return
        }
        if (epg.skipsReceiverSearch()) {
            results.update {
                if (it.request.query == request.query) it.copy(receiverPending = false) else it
            }
            return
        }
        results.update {
            if (it.request.query == request.query) it.copy(receiverInFlight = true) else it
        }
        val response = epg.receiverSearch(request.query)
        results.update {
            if (it.request.query == request.query) {
                it.copy(live = response, receiverPending = false, receiverInFlight = false)
            } else {
                it
            }
        }
    }

    /** The field's query to search. [nonce] makes a repeated immediate search distinct. */
    private data class SearchRequest(val query: String, val immediate: Boolean, val nonce: Int) {
        val searchable: Boolean
            get() = query.length >= if (immediate) 1 else MIN_TYPED_LENGTH
    }

    /** What Room and the receiver found for [request] so far. */
    private data class SearchResults(
        val request: SearchRequest,
        val cached: List<Event>? = null,
        val cacheDone: Boolean = false,
        val live: EnigmaResponse<List<Event>>? = null,
        val receiverPending: Boolean = request.searchable,
        val receiverInFlight: Boolean = false
    ) {
        fun applyTo(state: EpgSearchUiState): EpgSearchUiState {
            if (!request.searchable) {
                return state.copy(
                    showRecent = true,
                    sections = emptyList(),
                    cached = false,
                    searching = false,
                    emptyMessage = null,
                    retryable = false
                )
            }
            val liveEvents = live?.value
            val events = liveEvents ?: cached
            val searching = !cacheDone || receiverPending
            if (events == null && searching && !state.showRecent) {
                // Keep the previous query's results on screen until this one has some.
                return state.copy(searching = true, retryable = false, emptyMessage = null)
            }
            val emptyMessage = when {
                !events.isNullOrEmpty() -> null

                searching -> null

                events != null && liveEvents == null ->
                    UiText.Resource(R.string.epg_search_no_cached_match)

                events != null -> UiText.Resource(R.string.no_list_item)

                else -> live?.error.contentErrorText()
            }
            return state.copy(
                showRecent = false,
                sections = epgDaySections(events.orEmpty()),
                cached = liveEvents == null && cached != null,
                searching = searching,
                emptyMessage = emptyMessage,
                retryable = events == null && !searching
            )
        }
    }

    companion object {
        /** Pause before typing searches Room. */
        const val CACHE_SETTLE_MS = 250L

        /**
         * Pause before typing asks the receiver. `/web/epgsearch` scans the box's whole EPG
         * on its main thread, so only a real pause in typing sends one.
         */
        const val RECEIVER_SETTLE_MS = 1_200L

        /** Typed queries shorter than this list recent searches instead. */
        const val MIN_TYPED_LENGTH = 3

        private const val KEY_DRAFT = "epg_search_draft"
    }
}
