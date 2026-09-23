package net.reichholf.dreamdroid.ui.services

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import java.util.ArrayList
import net.reichholf.dreamdroid.ui.compose.ComposeRefreshState

/**
 * One movie location for [HubMovieListPage].
 * Key this ViewModel by location on the hub back-stack entry so a tab change
 * keeps the loaded list. [HubMovieListSession] stays the list model and the menu provider.
 */
class HubMovieListViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
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
            savedStateHandle[tagsKey()] = ArrayList(next)
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
        val saved = savedStateHandle.get<ArrayList<String>>(tagsKey()).orEmpty()
        selectedTags = saved.toList()
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

    private fun tagsKey(): String = "hub_movie_selected_tags:$locationKey"
}
