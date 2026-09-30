package net.reichholf.dreamdroid.ui.services

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BouquetListLoad
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.shouldWaitForDeviceInfo
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * The hub shell: its [mode], the tab row [selectedRow], and the tabs. [tvBouquets] and
 * [radioBouquets] are the user bouquets the receiver or Room gave; the UI appends the
 * dedicated roots with their names. [bouquetsLoaded] is false until a first strip
 * painted. The child lists report the title.
 */
data class HubUiState(
    val mode: String = HubModes.TV,
    val selectedRow: Int = 0,
    val tvBouquets: List<Service> = emptyList(),
    val radioBouquets: List<Service> = emptyList(),
    val bouquetsLoaded: Boolean = false,
    val bouquetError: UiText? = null,
    val movieLocations: List<String> = emptyList(),
    val locationsReady: Boolean = false,
    val timerRemountEpoch: Int = 0,
    val nowPlayingReloadEpoch: Int = 0,
    val userMessage: UiText? = null
)

/**
 * Hub mode, selected row, and bouquet or location tabs for [HubDestination]. The bouquet
 * strip loads again on each session change and after each bouquet edit
 * ([ServiceRepository.bouquetsEpoch]); loads run on [viewModelScope], so leaving the
 * destination and popping back keeps the strip. Child lists use their own ViewModels on
 * this same back-stack entry.
 */
@HiltViewModel
class HubViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val services: ServiceRepository,
    private val movies: MovieRepository,
    private val timers: TimerRepository,
    private val receiver: ReceiverRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private var currentTv: String?
    private var currentRadio: String?
    private var currentMovie: String?
    private var bouquetJob: Job? = null
    private var locationsJob: Job? = null
    private var lastLocationsHttpSuccess: Boolean? = null

    private val _uiState: MutableStateFlow<HubUiState>
    val uiState: StateFlow<HubUiState>

    init {
        val saved = readHubShellSaved(savedStateHandle)
        currentTv = saved.currentTv
        currentRadio = saved.currentRadio
        currentMovie = saved.currentMovie
        val knownLocations = profiles.locations().toList()
        _uiState = MutableStateFlow(
            HubUiState(
                mode = saved.mode,
                selectedRow = saved.selectedRow,
                movieLocations = knownLocations,
                locationsReady = knownLocations.isNotEmpty(),
                timerRemountEpoch = saved.timerRemountEpoch,
                nowPlayingReloadEpoch = saved.nowPlayingReloadEpoch
            )
        )
        uiState = _uiState.asStateFlow()
        viewModelScope.launch {
            combine(
                sessions.status.map { it.session }.distinctUntilChanged(),
                services.bouquetsEpoch
            ) { session, epoch -> session to epoch }.collect {
                bouquetJob?.cancel()
                bouquetJob = launch { loadBouquets() }
            }
        }
    }

    fun selectTv() {
        val (index, ref) = resolveBouquetSelection(tvTabs(), currentTv, defaultTvBouquet())
        currentTv = ref
        update { it.copy(mode = HubModes.TV, selectedRow = index) }
    }

    fun selectRadio() {
        val (index, ref) = resolveBouquetSelection(radioTabs(), currentRadio, null)
        currentRadio = ref
        update { it.copy(mode = HubModes.RADIO, selectedRow = index) }
    }

    /** Until the movie locations are known, shows row 0 and says they are loading. */
    fun selectMovies() {
        val state = _uiState.value
        if (!state.locationsReady || state.movieLocations.isEmpty()) {
            update {
                it.copy(
                    mode = HubModes.MOVIES,
                    selectedRow = 0,
                    userMessage = UiText.Resource(R.string.loading)
                )
            }
            return
        }
        update {
            it.copy(
                mode = HubModes.MOVIES,
                selectedRow = indexOfLocation(it.movieLocations, currentMovie)
            )
        }
    }

    fun selectTimer() {
        update { it.copy(mode = HubModes.TIMER, selectedRow = 0) }
    }

    /** Selects the tab at [index] of the current mode. */
    fun onRowSelected(index: Int) {
        val state = _uiState.value
        when (state.mode) {
            HubModes.TV -> currentTv = tvTabs().getOrNull(index)?.reference
            HubModes.RADIO -> currentRadio = radioTabs().getOrNull(index)?.reference
            HubModes.MOVIES -> currentMovie = state.movieLocations.getOrNull(index)
        }
        update { it.copy(selectedRow = index) }
    }

    /** A timer was edited; the timer list loads again. */
    fun bumpTimerRemount() {
        update { it.copy(timerRemountEpoch = it.timerRemountEpoch + 1) }
    }

    /** A zap finished; the now-playing strip loads again. */
    fun bumpNowPlayingReload() {
        update { it.copy(nowPlayingReloadEpoch = it.nowPlayingReloadEpoch + 1) }
    }

    /** Keeps [HubUiState.selectedRow] inside a row list of [rowCount]. */
    fun clampSelectedRow(rowCount: Int) {
        val selected = _uiState.value.selectedRow
        val next = when {
            rowCount > 0 && selected > rowCount - 1 -> rowCount - 1
            rowCount == 0 -> 0
            else -> return
        }
        if (next != selected) {
            update { it.copy(selectedRow = next) }
        }
    }

    /**
     * Loads the movie locations once; an empty failed load runs again on the next call,
     * which the destination makes each time it enters.
     */
    fun ensureLocations() {
        val state = _uiState.value
        val retry = shouldRetryHubLocations(
            state.locationsReady,
            state.movieLocations,
            locationsJob?.isActive == true,
            lastLocationsHttpSuccess
        )
        if (!retry) {
            return
        }
        locationsJob = viewModelScope.launch {
            val choices = timers.locationsAndTags()
            lastLocationsHttpSuccess = choices.locationsFromReceiver
            val painted = movies.locationsOrCached(
                receiverAnswered = choices.locationsFromReceiver,
                live = choices.locations
            )
            update {
                it.copy(
                    movieLocations = painted,
                    locationsReady = true,
                    selectedRow = if (it.mode == HubModes.MOVIES) {
                        indexOfLocation(painted, currentMovie)
                    } else {
                        it.selectedRow
                    }
                )
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private suspend fun loadBouquets() {
        val cached = services.cachedBouquets()
        val hasStrip = cached.tv.isNotEmpty() || cached.radio.isNotEmpty()
        if (hasStrip) {
            applyBouquets(cached.tv, cached.radio, error = null)
        }
        if (sessions.status.value.shouldSkipReceiverHttp(hasStrip)) {
            return
        }
        if (shouldWaitForDeviceInfo(hasStrip)) {
            receiver.awaitProfileCheck()
        }
        when (val load = services.bouquets()) {
            is BouquetListLoad.Loaded ->
                applyBouquets(load.bouquets.tv, load.bouquets.radio, error = null)

            is BouquetListLoad.Failed ->
                applyBouquets(emptyList(), emptyList(), load.error.contentErrorText())
        }
    }

    private fun applyBouquets(tv: List<Service>, radio: List<Service>, error: UiText?) {
        val state = _uiState.value
        val selected = when (state.mode) {
            HubModes.TV -> {
                val (index, ref) =
                    resolveBouquetSelection(tvTabs(tv), currentTv, defaultTvBouquet())
                currentTv = ref
                index
            }

            HubModes.RADIO -> {
                val (index, ref) = resolveBouquetSelection(radioTabs(radio), currentRadio, null)
                currentRadio = ref
                index
            }

            HubModes.MOVIES -> if (state.locationsReady) {
                indexOfLocation(state.movieLocations, currentMovie)
            } else {
                state.selectedRow
            }

            else -> 0
        }
        update {
            it.copy(
                tvBouquets = tv.toList(),
                radioBouquets = radio.toList(),
                bouquetsLoaded = true,
                bouquetError = error,
                selectedRow = selected
            )
        }
    }

    /**
     * The TV tabs as the UI lists them. Only the references matter here; the UI names the
     * dedicated roots.
     */
    private fun tvTabs(loaded: List<Service> = _uiState.value.tvBouquets): List<Service> =
        tabs(loaded, services.tvRoots)

    private fun radioTabs(loaded: List<Service> = _uiState.value.radioBouquets): List<Service> =
        tabs(loaded, services.radioRoots)

    private fun tabs(loaded: List<Service>, roots: List<String>): List<Service> {
        val refs = roots.toTypedArray()
        return buildDedicatedBouquets(loaded, refs, refs)
    }

    private fun defaultTvBouquet(): String? = profiles.current.value?.defaultBouquetTv

    /** Applies [transform] and saves the shell state. */
    private fun update(transform: (HubUiState) -> HubUiState) {
        val state = _uiState.updateAndGet(transform)
        HubShellSaved(
            mode = state.mode,
            currentTv = currentTv,
            currentRadio = currentRadio,
            currentMovie = currentMovie,
            selectedRow = state.selectedRow,
            timerRemountEpoch = state.timerRemountEpoch,
            nowPlayingReloadEpoch = state.nowPlayingReloadEpoch
        ).writeTo(savedStateHandle)
    }
}

private fun indexOfLocation(items: List<String>, location: String?): Int {
    if (location.isNullOrEmpty() || items.isEmpty()) return 0
    val idx = items.indexOf(location)
    return if (idx >= 0) idx else 0
}
