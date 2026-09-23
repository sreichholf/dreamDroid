package net.reichholf.dreamdroid.ui.services

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState

/**
 * One movie location for [HubMovieListPage].
 * Key this ViewModel by location on the hub back-stack entry so a tab change
 * keeps the loaded list. [HubMovieListSession] stays the list model and the menu provider.
 */
class HubMovieListViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    private val savedAccess = HandleHubMovieListSavedAccess(savedStateHandle)
    val session: HubMovieListSession = HubMovieListSession()

    var emptyMessage by mutableStateOf<String?>(null)
        private set

    var selectedTags by mutableStateOf<List<String>>(emptyList())
        private set

    private var locationKey: String = ""
    private var locationBound: Boolean = false
    private var loaded: Boolean = false

    init {
        session.listState = MovieListState()
        session.refresh = ComposeRefreshState()
        session.scope = viewModelScope
        session.onEmptyMessage = { emptyMessage = it }
        session.onSelectedTags = { next ->
            selectedTags = next
            if (locationKey.isNotEmpty()) {
                writeHubMovieSelectedTags(savedAccess, locationKey, next)
            }
        }
    }

    fun bindLocation(location: String, locationIndex: Int) {
        if (locationBound) {
            return
        }
        locationBound = true
        locationKey = location
        session.location = location
        session.locationIndex = locationIndex
        selectedTags = readHubMovieSelectedTags(savedAccess, location)
        session.selectedTags = ArrayList(selectedTags)
    }

    fun ensureLoaded() {
        if (loaded) {
            return
        }
        loaded = true
        session.reload()
    }

    override fun onCleared() {
        session.cancelInFlight()
        super.onCleared()
    }
}

private class HandleHubMovieListSavedAccess(private val handle: SavedStateHandle) :
    HubMovieListSavedAccess {
    override fun getSelectedTags(location: String): List<String>? {
        val stored = handle.get<ArrayList<String>>(hubMovieSelectedTagsKey(location))
        return stored?.toList()
    }

    override fun setSelectedTags(location: String, tags: List<String>) {
        handle[hubMovieSelectedTagsKey(location)] = ArrayList(tags)
    }
}
