package net.reichholf.dreamdroid.ui.epg

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.loadEventList
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

private const val EPG_SEARCH_DRAFT_KEY = "epg_search_draft"

/**
 * Search-field draft for [EpgSearchDestination].
 *
 * Matches `rememberSaveable(query, remountEpoch)`: a new route query or remount epoch
 * replaces the draft with [query]. The same query and epoch keep [draft].
 */
fun epgSearchDraftForRoute(
    draft: String,
    previousQuery: String,
    previousEpoch: Int,
    query: String,
    remountEpoch: Int
): String {
    if (previousQuery != query || previousEpoch != remountEpoch) {
        return query
    }
    return draft
}

/**
 * EPG search list, in-progress draft, and load job for [EpgSearchDestination].
 * The search bar expanded flag stays in the composable.
 */
class EpgSearchViewModel(application: Application, private val savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    var listState by mutableStateOf(EpgBouquetListState())
        private set

    var draftQuery by mutableStateOf("")
        private set

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    var refreshing by mutableStateOf(false)
        private set

    private var boundQuery: String? = null
    private var boundEpoch: Int? = null
    private var hasSavedDraft: Boolean = false
    private var loadToken: Int = 0
    private var loadJob: Job? = null

    init {
        val saved = savedStateHandle.get<String>(EPG_SEARCH_DRAFT_KEY)
        hasSavedDraft = saved != null
        if (saved != null) {
            draftQuery = saved
        }
    }

    /**
     * Applies the route [query] and [remountEpoch]. The first call keeps a restored draft.
     * A later change of either value resets the draft to [query] and reloads.
     */
    fun syncRoute(query: String, remountEpoch: Int) {
        val previousQuery = boundQuery
        val previousEpoch = boundEpoch
        if (previousQuery == null || previousEpoch == null) {
            if (!hasSavedDraft) {
                onDraftQueryChange(query)
            }
            boundQuery = query
            boundEpoch = remountEpoch
            reload()
            return
        }
        if (previousQuery == query && previousEpoch == remountEpoch) {
            return
        }
        val nextDraft = epgSearchDraftForRoute(
            draft = draftQuery,
            previousQuery = previousQuery,
            previousEpoch = previousEpoch,
            query = query,
            remountEpoch = remountEpoch
        )
        boundQuery = query
        boundEpoch = remountEpoch
        onDraftQueryChange(nextDraft)
        listState = EpgBouquetListState()
        emptyMessage = null
        reload()
    }

    fun onDraftQueryChange(value: String) {
        draftQuery = value
        savedStateHandle[EPG_SEARCH_DRAFT_KEY] = value
    }

    fun reload() {
        val query = boundQuery.orEmpty()
        val token = ++loadToken
        if (query.isEmpty()) {
            loadJob?.cancel()
            loadJob = null
            refreshing = false
            return
        }
        val app = getApplication<Application>()
        if (listState.items.isEmpty()) {
            emptyMessage = app.getString(R.string.loading)
        } else {
            emptyMessage = null
        }
        refreshing = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = loadEventList(
                app,
                listOf(NameValuePair("search", query)),
                URIStore.EPG_SEARCH
            )
            if (token != loadToken) {
                return@launch
            }
            refreshing = false
            if (!result.success) {
                listState.replaceAll(emptyList())
                emptyMessage = result.errorText
                return@launch
            }
            if (result.events.isEmpty()) {
                listState.replaceAll(emptyList())
                emptyMessage = app.getString(R.string.no_list_item)
            } else {
                emptyMessage = null
                listState.replaceAll(result.events)
            }
        }
    }
}
