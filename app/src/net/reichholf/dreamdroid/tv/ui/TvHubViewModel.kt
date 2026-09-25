package net.reichholf.dreamdroid.tv.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

interface TvHubLoader {
    suspend fun browse(): TvHubBrowseResult

    suspend fun movies(dirname: String): TvHubMoviesResult
}

private class ApplicationTvHubLoader(private val app: Application) : TvHubLoader {
    override suspend fun browse(): TvHubBrowseResult = loadTvHubBrowse(app)

    override suspend fun movies(dirname: String): TvHubMoviesResult = loadTvHubMovies(app, dirname)
}

/**
 * Selected header, bouquet rows, per-location movies, and the open bouquet
 * Info/Menu overlay or timer editor for [ComposeTvHubApp], scoped to the TV
 * activity. Each new [ConnectionStatus.Session] reloads the browse data;
 * switching headers keeps movies already loaded for a location. Overlay state
 * survives configuration changes with this activity-scoped ViewModel.
 */
class TvHubViewModel(private val loader: TvHubLoader, sessions: Flow<ConnectionStatus.Session?>) :
    ViewModel() {
    var selectedHeaderId by mutableStateOf(TvComposeHubHost.HEADER_SETTINGS_ID)
        private set

    var loading by mutableStateOf(true)
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    var bouquetRows by mutableStateOf<List<HubBouquetRow>>(emptyList())
        private set

    var movieLocations by mutableStateOf<List<String>>(emptyList())
        private set

    var moviesByLocation by mutableStateOf<Map<String, List<Movie>>>(emptyMap())
        private set

    var movieLoading by mutableStateOf(false)
        private set

    var movieError by mutableStateOf<String?>(null)
        private set

    var serviceTimerTarget by mutableStateOf<Pair<ServiceNowNext, String?>?>(null)
        private set

    var editTimerEvent by mutableStateOf<Event?>(null)
        private set

    private var browseJob: Job? = null
    private var movieJob: Job? = null
    private var movieJobDirname: String? = null

    init {
        viewModelScope.launch {
            sessions.distinctUntilChanged().collect { reload() }
        }
    }

    fun reload() {
        browseJob?.cancel()
        movieJob?.cancel()
        movieJobDirname = null
        loading = true
        errorText = null
        movieError = null
        moviesByLocation = emptyMap()
        browseJob = viewModelScope.launch {
            val result = loader.browse()
            loading = false
            errorText = unavailableTvHubMessage(
                result.usedCache,
                result.rows.isNotEmpty(),
                result.errorText
            )
            bouquetRows = result.rows
            movieLocations = result.locations
            val stillValid = TvComposeHubHost.isPersistentHubHeader(selectedHeaderId) ||
                bouquetRows.any { it.bouquet.reference == selectedHeaderId } ||
                TvComposeHubHost.movieDirnameFromHeader(selectedHeaderId) in movieLocations
            if (!stillValid) {
                selectedHeaderId = TvComposeHubHost.HEADER_SETTINGS_ID
            }
        }
        loadSelectedMovies()
    }

    fun selectHeader(headerId: String) {
        if (headerId == selectedHeaderId) {
            return
        }
        selectedHeaderId = headerId
        loadSelectedMovies()
    }

    fun showServiceTimer(service: ServiceNowNext, bouquetRef: String?) {
        serviceTimerTarget = service to bouquetRef
    }

    fun dismissServiceTimer() {
        serviceTimerTarget = null
    }

    fun showEditTimer(event: Event) {
        editTimerEvent = event
    }

    fun dismissEditTimer() {
        editTimerEvent = null
    }

    // Leanback parity: load movies for a location only when its header is selected.
    private fun loadSelectedMovies() {
        val dirname = TvComposeHubHost.movieDirnameFromHeader(selectedHeaderId) ?: return
        if (dirname in moviesByLocation) {
            return
        }
        if (movieJob?.isActive == true && movieJobDirname == dirname) {
            return
        }
        movieJob?.cancel()
        movieJobDirname = dirname
        movieLoading = true
        movieError = null
        movieJob = viewModelScope.launch {
            val result = loader.movies(dirname)
            movieLoading = false
            movieError = unavailableTvHubMessage(
                result.usedCache,
                !result.movies.isNullOrEmpty(),
                result.errorText
            )
            if (result.movies != null) {
                moviesByLocation = moviesByLocation + (dirname to result.movies)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                TvHubViewModel(
                    ApplicationTvHubLoader(app),
                    SessionConnectionHolder.shared.status.map { it.session }
                )
            }
        }
    }
}
