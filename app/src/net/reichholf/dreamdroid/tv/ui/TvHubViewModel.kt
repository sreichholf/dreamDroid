package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** The bouquet service whose INFO/MENU overlay is open, and the bouquet it was opened in. */
data class TvServiceTimerTarget(val service: ServiceNowNext, val bouquetRef: String?)

/**
 * The TV hub: the selected drawer header, the bouquet rows and movie locations, the movies
 * loaded per location, the open service overlay or timer editor, and the session.
 * [hasCache] is null until Room answered for the active profile. [receiverLabel] names the
 * receiver on the failed ProfileCheck gate.
 */
data class TvHubUiState(
    val selectedHeaderId: String = TvComposeHubHost.HEADER_SETTINGS_ID,
    val loading: Boolean = true,
    val errorText: UiText? = null,
    val bouquetRows: List<HubBouquetRow> = emptyList(),
    val movieLocations: List<String> = emptyList(),
    val moviesByLocation: Map<String, List<Movie>> = emptyMap(),
    val movieLoading: Boolean = false,
    val movieError: UiText? = null,
    val serviceTimerTarget: TvServiceTimerTarget? = null,
    val editTimerEvent: Event? = null,
    val settingTimer: Boolean = false,
    val userMessage: UiText? = null,
    val connection: ConnectionStatus = ConnectionStatus(),
    val hasCache: Boolean? = null,
    val receiverLabel: String = ""
) {
    val streamingEnabled: Boolean
        get() = connection.allowsStreaming()

    val browseError: UiText?
        get() = errorText ?: movieError
}

/**
 * State of the TV hub, scoped to the TV activity so it survives configuration changes and
 * the hub leaving composition. Each new [ConnectionStatus.Session] reloads the browse data;
 * switching headers keeps movies already loaded for a location.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvHubViewModel @Inject constructor(
    private val browse: TvHubBrowse,
    private val timers: TimerRepository,
    profiles: ProfileRepository,
    services: ServiceRepository,
    sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(TvHubUiState())

    private val sessionWord = sessions.status.map { it.session }.distinctUntilChanged()

    /**
     * Whether Room can paint the hub, and for which profile id. Refreshed on each profile or
     * session change; unknown until Room answered.
     */
    private val cache: StateFlow<Pair<Int?, Boolean?>> =
        combine(profiles.current, sessionWord) { profile, _ -> profile?.id }
            .mapLatest<Int?, Pair<Int?, Boolean?>> { id ->
                id to (id?.let { services.hasCache(it) } ?: false)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UNKNOWN_CACHE)

    val uiState: StateFlow<TvHubUiState> = combine(
        _uiState,
        sessions.status,
        profiles.current,
        cache
    ) { state, connection, profile, (cacheProfileId, hasCache) ->
        state.copy(
            connection = connection,
            hasCache = hasCache.takeIf { profile?.id == cacheProfileId },
            receiverLabel = profile?.let { "${it.user}@${it.host}:${it.port}" }.orEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)

    private var browseJob: Job? = null
    private var movieJob: Job? = null
    private var movieJobDirname: String? = null

    init {
        viewModelScope.launch {
            sessionWord.collect { reload() }
        }
    }

    fun reload() {
        browseJob?.cancel()
        movieJob?.cancel()
        movieJobDirname = null
        _uiState.update {
            it.copy(
                loading = true,
                errorText = null,
                movieError = null,
                moviesByLocation = emptyMap()
            )
        }
        browseJob = viewModelScope.launch {
            val result = browse.browse()
            _uiState.update { state ->
                val stillValid = TvComposeHubHost.isPersistentHubHeader(state.selectedHeaderId) ||
                    result.rows.any { it.bouquet.reference == state.selectedHeaderId } ||
                    TvComposeHubHost.movieDirnameFromHeader(state.selectedHeaderId) in
                    result.locations
                state.copy(
                    loading = false,
                    errorText = unavailableTvHubMessage(
                        result.usedCache,
                        result.rows.isNotEmpty(),
                        result.errorText
                    ),
                    bouquetRows = result.rows,
                    movieLocations = result.locations,
                    selectedHeaderId = if (stillValid) {
                        state.selectedHeaderId
                    } else {
                        TvComposeHubHost.HEADER_SETTINGS_ID
                    }
                )
            }
        }
        loadSelectedMovies()
    }

    fun selectHeader(headerId: String) {
        if (headerId == _uiState.value.selectedHeaderId) {
            return
        }
        _uiState.update { it.copy(selectedHeaderId = headerId) }
        loadSelectedMovies()
    }

    fun showServiceTimer(service: ServiceNowNext, bouquetRef: String?) {
        _uiState.update { it.copy(serviceTimerTarget = TvServiceTimerTarget(service, bouquetRef)) }
    }

    fun dismissServiceTimer() {
        _uiState.update { it.copy(serviceTimerTarget = null) }
    }

    fun showEditTimer(event: Event) {
        _uiState.update { it.copy(editTimerEvent = event) }
    }

    fun dismissEditTimer() {
        _uiState.update { it.copy(editTimerEvent = null) }
    }

    /** The editor saved a timer: close it and the overlay it was opened from. */
    fun onTimerSaved() {
        _uiState.update { it.copy(editTimerEvent = null, serviceTimerTarget = null) }
    }

    /**
     * Adds a timer for [event] by its event id, then closes the overlay. The receiver's
     * answer is the user message.
     */
    fun setTimer(event: Event) {
        if (_uiState.value.settingTimer) {
            return
        }
        _uiState.update { it.copy(settingTimer = true) }
        viewModelScope.launch {
            val response = timers.addByEvent(event)
            _uiState.update {
                it.copy(
                    settingTimer = false,
                    serviceTimerTarget = null,
                    userMessage = response.userMessageText()
                )
            }
        }
    }

    /** No app on the device plays the stream. */
    fun onMissingStreamPlayer() {
        _uiState.update { it.copy(userMessage = UiText.Resource(R.string.missing_stream_player)) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    // Leanback parity: load movies for a location only when its header is selected.
    private fun loadSelectedMovies() {
        val state = _uiState.value
        val dirname = TvComposeHubHost.movieDirnameFromHeader(state.selectedHeaderId) ?: return
        if (dirname in state.moviesByLocation) {
            return
        }
        if (movieJob?.isActive == true && movieJobDirname == dirname) {
            return
        }
        movieJob?.cancel()
        movieJobDirname = dirname
        _uiState.update { it.copy(movieLoading = true, movieError = null) }
        movieJob = viewModelScope.launch {
            val result = browse.movies(dirname)
            _uiState.update { current ->
                current.copy(
                    movieLoading = false,
                    movieError = unavailableTvHubMessage(
                        result.usedCache,
                        !result.movies.isNullOrEmpty(),
                        result.errorText
                    ),
                    moviesByLocation = if (result.movies != null) {
                        current.moviesByLocation + (dirname to result.movies)
                    } else {
                        current.moviesByLocation
                    }
                )
            }
        }
    }

    private companion object {
        val UNKNOWN_CACHE: Pair<Int?, Boolean?> = Pair(-1, null)
    }
}
