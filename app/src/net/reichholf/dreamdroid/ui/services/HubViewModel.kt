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
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.data.BouquetListLoad
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
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
    val nowPlayingReloadEpoch: Int = 0
)

/**
 * Hub mode, selected row, and bouquet or location tabs for [HubDestination]. The bouquet
 * strip loads again on each session change and after each edit of a bouquet index
 * ([ServiceRepository.bouquetEdits]); loads run on [viewModelScope], so leaving the
 * destination and popping back keeps the strip. Child lists use their own ViewModels on
 * this same back-stack entry.
 */
@HiltViewModel
class HubViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val services: ServiceRepository,
    private val movies: MovieRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private var currentTv: String?
    private var currentRadio: String?
    private var currentMovie: String?
    private var bouquetJob: Job? = null
    private var locationsJob: Job? = null
    private var locationsFromReceiver = false

    private val _uiState: MutableStateFlow<HubUiState>
    val uiState: StateFlow<HubUiState>

    init {
        val saved = readHubShellSaved(savedStateHandle)
        currentTv = saved.currentTv
        currentRadio = saved.currentRadio
        currentMovie = saved.currentMovie
        _uiState = MutableStateFlow(
            HubUiState(
                mode = saved.mode,
                selectedRow = saved.selectedRow,
                nowPlayingReloadEpoch = saved.nowPlayingReloadEpoch
            )
        )
        uiState = _uiState.asStateFlow()
        viewModelScope.launch {
            combine(
                sessions.status.map { it.session }.distinctUntilChanged(),
                services.bouquetEdits
                    .map { edits -> services.bouquetIndexRefs.map { edits[it] } }
                    .distinctUntilChanged()
            ) { session, edits -> session to edits }.collect {
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

    /** Until the movie locations are known, shows row 0; the page shows them loading. */
    fun selectMovies() {
        update { it.copy(mode = HubModes.MOVIES, selectedRow = movieRow(it)) }
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
     * Loads the movie locations ([MovieRepository.locations]) until the receiver answered;
     * the destination calls this each time it enters.
     */
    fun ensureLocations() {
        if (!shouldLoadHubLocations(locationsJob?.isActive == true, locationsFromReceiver)) {
            return
        }
        locationsJob = viewModelScope.launch {
            movies.locations().collect { locations ->
                locationsFromReceiver = locations.fromReceiver
                update {
                    it.copy(
                        movieLocations = locations.names,
                        locationsReady = true,
                        selectedRow = if (it.mode == HubModes.MOVIES) {
                            indexOfLocation(locations.names, currentMovie)
                        } else {
                            it.selectedRow
                        }
                    )
                }
            }
        }
    }

    private suspend fun loadBouquets() {
        services.hubBouquets().collect { load ->
            when (load) {
                is BouquetListLoad.Loaded ->
                    applyBouquets(load.bouquets.tv, load.bouquets.radio, error = null)

                is BouquetListLoad.Failed ->
                    applyBouquets(emptyList(), emptyList(), load.error.contentErrorText())
            }
        }
    }

    private fun applyBouquets(tv: List<Service>, radio: List<Service>, error: UiText?) {
        // Derive the row from the state being replaced, not an earlier snapshot: the movie
        // locations can land in between.
        update { state ->
            state.copy(
                tvBouquets = tv.toList(),
                radioBouquets = radio.toList(),
                bouquetsLoaded = true,
                bouquetError = error,
                selectedRow = when (state.mode) {
                    HubModes.TV -> {
                        val (index, ref) =
                            resolveBouquetSelection(tvTabs(tv), currentTv, defaultTvBouquet())
                        currentTv = ref
                        index
                    }

                    HubModes.RADIO -> {
                        val (index, ref) =
                            resolveBouquetSelection(radioTabs(radio), currentRadio, null)
                        currentRadio = ref
                        index
                    }

                    HubModes.MOVIES ->
                        if (state.locationsReady) movieRow(state) else state.selectedRow

                    else -> 0
                }
            )
        }
    }

    /** Row 0 until the movie locations are known, then the row of [currentMovie]. */
    private fun movieRow(state: HubUiState): Int = if (state.locationsReady) {
        indexOfLocation(state.movieLocations, currentMovie)
    } else {
        0
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
            nowPlayingReloadEpoch = state.nowPlayingReloadEpoch
        ).writeTo(savedStateHandle)
    }
}

private fun indexOfLocation(items: List<String>, location: String?): Int {
    if (location.isNullOrEmpty() || items.isEmpty()) return 0
    val idx = items.indexOf(location)
    return if (idx >= 0) idx else 0
}
