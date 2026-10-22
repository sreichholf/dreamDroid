package net.reichholf.dreamdroid.tv.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LastLiveStream
import net.reichholf.dreamdroid.data.LastLiveStreamRepository
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
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

/** A stream the hub hands to the player once. */
sealed interface TvStreamOpen {
    /** Play [stream] of [service], a channel of the list [bouquetRef]. */
    data class Service(
        val service: ServiceNowNext,
        val bouquetRef: String?,
        val stream: LiveStream.Ready
    ) : TvStreamOpen

    /** Play the recording [movie] from [url]. */
    data class Recording(val movie: Movie, val url: String) : TvStreamOpen
}

/**
 * The TV hub: the section on screen ([navigation]), the bouquet rows and movie locations, the
 * movies loaded per location, the open service overlay or timer editor, the active profile's
 * name and the channel it played last ([lastStream], Live TV's "Continue watching"), and the
 * session.
 */
data class TvHubUiState(
    val navigation: HubNavigation = HubNavigation(),
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
    val stream: TvStreamOpen? = null,
    val profileName: String = "",
    val lastStream: LastLiveStream? = null,
    val connection: ConnectionStatus = ConnectionStatus()
) {
    val section: HubSection
        get() = navigation.section

    val streamingEnabled: Boolean
        get() = connection.allowsStreaming()

    val browseError: UiText?
        get() = errorText ?: movieError
}

/**
 * State of the TV hub, scoped to the TV activity so it survives configuration changes and
 * the hub leaving composition. [TvHubUiState.stream] is a stream for the player; the host
 * starts it and calls [onStreamStarted]. Each new [ConnectionStatus.Session] reloads the browse data,
 * except Online after a browse that already asked the receiver while the session was unknown;
 * switching sections keeps movies the receiver already sent for a location. While the hub is
 * shown ([onShown]) and a browse got a receiver answer, the EPG for the coming week of the Live
 * TV bouquet on screen is synced ([TvHubBrowse.syncEpg]) once it stayed selected for
 * [EPG_SYNC_DELAY_MS], so moving through the drawer with the D-pad syncs nothing.
 */
@HiltViewModel
class TvHubViewModel internal constructor(
    private val browse: TvHubBrowse,
    private val timers: TimerRepository,
    private val receiver: ReceiverRepository,
    private val movies: MovieRepository,
    private val sessions: SessionConnectionHolder,
    profiles: ProfileRepository,
    lastStreams: LastLiveStreamRepository,
    private val epgSyncDelayMs: Long
) : ViewModel() {
    @Inject
    constructor(
        browse: TvHubBrowse,
        timers: TimerRepository,
        receiver: ReceiverRepository,
        movies: MovieRepository,
        sessions: SessionConnectionHolder,
        profiles: ProfileRepository,
        lastStreams: LastLiveStreamRepository
    ) : this(browse, timers, receiver, movies, sessions, profiles, lastStreams, EPG_SYNC_DELAY_MS)

    private val _uiState = MutableStateFlow(TvHubUiState())

    private val sessionWord = sessions.status.map { it.session }.distinctUntilChanged()

    val uiState: StateFlow<TvHubUiState> = combine(
        _uiState,
        sessions.status,
        profiles.current,
        lastStreams.lastStream
    ) { state, status, profile, lastStream ->
        state.copy(
            connection = status,
            profileName = profile?.name.orEmpty(),
            lastStream = lastStream
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)

    private var browseJob: Job? = null

    /** The session the current browse started in, and whether it got a receiver answer. */
    private var browseSession: ConnectionStatus.Session? = null
    private var browseLive = false
    private var movieJob: Job? = null
    private var movieJobDirname: String? = null
    private var streamJob: Job? = null

    /** Locations whose movies came from the receiver; Room's snapshot is asked again. */
    private val liveMovieLocations = mutableSetOf<String>()

    private val shown = MutableStateFlow(false)

    /** Browses the receiver answered; each one starts the EPG sync again. */
    private val liveBrowses = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            sessionWord.collect { session ->
                if (!browseCoversOnline(session)) {
                    reload()
                }
            }
        }
        viewModelScope.launch {
            combine(
                shown,
                liveBrowses,
                _uiState.map { it.navigation.liveBouquetRef }.distinctUntilChanged()
            ) { shown, live, ref -> ref?.takeIf { shown && live > 0 }?.let { it to live } }
                .syncWhenSettled(epgSyncDelayMs) { syncEpg(it.first) }
        }
    }

    /** The hub is on screen; the EPG sync of the selected bouquet may run. */
    fun onShown() {
        shown.value = true
    }

    /** The hub left the screen; stops the EPG sync. */
    fun onHidden() {
        shown.value = false
    }

    fun reload() {
        browseJob?.cancel()
        movieJob?.cancel()
        movieJobDirname = null
        liveMovieLocations.clear()
        browseSession = sessions.status.value.session
        browseLive = false
        _uiState.update {
            it.copy(
                loading = true,
                errorText = null,
                movieError = null,
                moviesByLocation = emptyMap()
            )
        }
        browseJob = viewModelScope.launch {
            browse.browse().collect { result -> applyBrowse(result) }
        }
        loadSelectedMovies()
    }

    /**
     * A cold start browses while the profile check runs; the session is not Offline then, so
     * that browse asks the receiver. When the check then says Online, it already covers it.
     */
    private fun browseCoversOnline(session: ConnectionStatus.Session?): Boolean =
        session == ConnectionStatus.Session.Online &&
            browseJob != null &&
            browseSession == null &&
            (browseJob?.isActive == true || browseLive)

    private fun applyBrowse(result: TvHubBrowseResult) {
        browseLive = browseLive || !result.usedCache
        if (!result.usedCache) {
            liveBrowses.update { it + 1 }
        }
        val location = _uiState.value.navigation.recordingsLocation
        _uiState.update { state ->
            state.copy(
                loading = false,
                errorText = unavailableTvHubMessage(
                    result.usedCache,
                    result.rows.isNotEmpty(),
                    result.errorText
                ),
                bouquetRows = result.rows,
                movieLocations = result.locations,
                navigation = state.navigation.reconcile(result.rows, result.locations)
            )
        }
        if (_uiState.value.navigation.recordingsLocation != location) {
            loadSelectedMovies()
        }
    }

    /** Opens the top bar tab [tab]. */
    fun openTab(tab: HubTab) = navigate { state ->
        state.navigation.open(tab, state.bouquetRows, state.movieLocations)
    }

    /** Opens Settings, the top bar's trailing icon. */
    fun openSettings() = navigate { it.navigation.openSettings() }

    /** Selects the drawer row [id]: a bouquet on Live TV or Guide, a location on Recordings. */
    fun selectDrawerItem(id: String) = navigate { it.navigation.select(id) }

    /** Back from any section but Live TV returns to Live TV. False on Live TV. */
    fun back(): Boolean {
        val target = _uiState.value.navigation.back() ?: return false
        navigate { target }
        return true
    }

    private fun navigate(transform: (TvHubUiState) -> HubNavigation) {
        val before = _uiState.value.navigation
        _uiState.update { it.copy(navigation = transform(it)) }
        if (_uiState.value.navigation != before) {
            loadSelectedMovies()
        }
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

    /**
     * Streams [service] of the list [bouquetRef] once the receiver can
     * ([ReceiverRepository.liveStream]); otherwise the receiver's answer is the user message.
     */
    fun streamService(service: ServiceNowNext, bouquetRef: String?) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            when (val stream = receiver.liveStream(service.serviceReference)) {
                is LiveStream.Ready -> _uiState.update {
                    it.copy(stream = TvStreamOpen.Service(service, bouquetRef, stream))
                }

                is LiveStream.Failed -> _uiState.update { it.copy(userMessage = stream.message) }
            }
        }
    }

    /** Streams the recording [movie]. */
    fun streamMovie(movie: Movie) {
        streamJob?.cancel()
        _uiState.update {
            it.copy(stream = TvStreamOpen.Recording(movie, movies.streamUrl(movie)))
        }
    }

    /** The host handed [TvHubUiState.stream] to the player. */
    fun onStreamStarted() {
        _uiState.update { it.copy(stream = null) }
    }

    /** No app on the device plays the stream. */
    fun onMissingStreamPlayer() {
        _uiState.update { it.copy(userMessage = UiText.Resource(R.string.missing_stream_player)) }
    }

    fun showMessage(message: UiText) {
        _uiState.update { it.copy(userMessage = message) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    // Leanback parity: load movies for a location only when Recordings shows it.
    private fun loadSelectedMovies() {
        val dirname = _uiState.value.navigation.recordingsLocation ?: return
        if (dirname in liveMovieLocations) {
            return
        }
        if (movieJob?.isActive == true && movieJobDirname == dirname) {
            return
        }
        movieJob?.cancel()
        movieJobDirname = dirname
        _uiState.update { it.copy(movieLoading = true, movieError = null) }
        movieJob = viewModelScope.launch {
            browse.movies(dirname).collect { result -> applyMovies(dirname, result) }
        }
    }

    private fun applyMovies(dirname: String, result: TvHubMoviesResult) {
        if (result.movies != null && !result.usedCache) {
            liveMovieLocations += dirname
        }
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

    /** Best effort: the rows already show the receiver's now/next. */
    private suspend fun syncEpg(ref: String) {
        try {
            browse.syncEpg(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(DreamDroid.LOG_TAG, "TV hub EPG sync failed", e)
        }
    }

    companion object {
        /** How long a bouquet header stays selected before its EPG sync starts. */
        const val EPG_SYNC_DELAY_MS: Long = 1_000L
    }
}

/**
 * Runs [sync] for each value that stays for [settleMs]. A new value cancels the wait or the
 * running sync; null only cancels.
 */
internal suspend fun <T : Any> Flow<T?>.syncWhenSettled(settleMs: Long, sync: suspend (T) -> Unit) {
    distinctUntilChanged().collectLatest { target ->
        if (target != null) {
            delay(settleMs)
            sync(target)
        }
    }
}
