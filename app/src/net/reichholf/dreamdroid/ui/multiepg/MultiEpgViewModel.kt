package net.reichholf.dreamdroid.ui.multiepg

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.TimerListResult
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.multiepg.MultiEpgGrid
import net.reichholf.dreamdroid.multiepg.MultiEpgGridState
import net.reichholf.dreamdroid.multiepg.MultiEpgPersistGate
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

const val MULTI_EPG_VISIBLE_MINUTES_KEY: String = "multi_epg_visible_minutes"

/** The phone MultiEPG destination: the bouquet it shows, the loaded grid, and the zoom. */
data class MultiEpgUiState(
    val bouquetName: String = "",
    val grid: MultiEpgGridState = MultiEpgGridState(),
    val visibleMinutes: Int = MULTI_EPG_VISIBLE_MINUTES
) {
    val title: UiText
        get() = if (bouquetName.isBlank()) {
            UiText.Resource(R.string.multiepg)
        } else {
            UiText.Raw(bouquetName)
        }
}

/**
 * Owns the loaded [MultiEpgGrid] and the saved visible-minute span. Load work uses
 * [viewModelScope], so leaving the destination does not cancel it.
 */
@HiltViewModel
class MultiEpgViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val epg: EpgRepository,
    timers: TimerRepository,
    profiles: ProfileRepository,
    sessions: SessionConnectionHolder
) : ViewModel() {
    private val persistGate = epg.multiEpgPersistGate()
    private val grid = newMultiEpgGrid(
        viewModelScope,
        epg,
        timers,
        profiles,
        sessions,
        persistGate
    )
    private val _uiState = MutableStateFlow(
        MultiEpgUiState(visibleMinutes = readMultiEpgVisibleMinutes(savedStateHandle))
    )
    val uiState: StateFlow<MultiEpgUiState> = combine(_uiState, grid.state) { state, grid ->
        state.copy(grid = grid)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)

    private var bouquetRef: String = ""
    private var appliedKey: Pair<Int, String>? = null
    private var inFlightKey: Pair<Int, String>? = null
    private var loadGeneration: Int = 0
    private var loadJob: Job? = null

    /**
     * Loads [bouquetRef] at [anchorSec] when [remountEpoch] or the bouquet changed.
     * A repeat call for the same pair leaves the in-flight or finished load alone.
     */
    fun ensureLoaded(remountEpoch: Int, bouquetRef: String, bouquetName: String, anchorSec: Long) {
        _uiState.update { it.copy(bouquetName = bouquetName) }
        this.bouquetRef = bouquetRef
        val key = remountEpoch to bouquetRef
        if (appliedKey == key || inFlightKey == key) {
            return
        }
        val generation = ++loadGeneration
        inFlightKey = key
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                persistGate.knownTabRefs = epg.hubTabStripRefs()
                if (generation != loadGeneration) {
                    return@launch
                }
                appliedKey = key
                grid.replaceAndLoad(bouquetRef, anchorSec)
            } finally {
                if (generation == loadGeneration) {
                    inFlightKey = null
                }
            }
        }
    }

    /** Reloads the bouquet with [nowSec] as the new left edge. */
    fun jumpToNow(nowSec: Long) {
        grid.replaceAndLoad(bouquetRef, nowSec)
    }

    fun previousDay() {
        val state = grid.state.value
        grid.focusAt(maxOf(state.originFloorSec, state.anchorSec - MultiEpgWindows.CHUNK_SECONDS))
    }

    fun nextDay() {
        grid.focusAt(grid.state.value.anchorSec + MultiEpgWindows.CHUNK_SECONDS)
    }

    /** Pull-to-refresh: refetches the chunk in view even when Room is fresh. */
    fun refresh() {
        grid.load(grid.state.value.anchorSec, forceRefresh = true, isPull = true)
    }

    fun onVisibleWindow(startSec: Long, endSec: Long) {
        grid.onVisibleWindow(startSec, endSec)
    }

    fun onVisibleMinutesChange(minutes: Int) {
        _uiState.update { it.copy(visibleMinutes = minutes) }
        writeMultiEpgVisibleMinutes(savedStateHandle, minutes)
    }
}

/**
 * A [MultiEpgGrid] over the process's MultiEPG cache, as the phone and TV ViewModels use it.
 * [persistGate] decides which bouquets land in Room.
 */
internal fun newMultiEpgGrid(
    scope: CoroutineScope,
    epg: EpgRepository,
    timers: TimerRepository,
    profiles: ProfileRepository,
    sessions: SessionConnectionHolder,
    persistGate: MultiEpgPersistGate
): MultiEpgGrid = MultiEpgGrid(
    sync = epg.multiEpgSync,
    scope = scope,
    profileId = { profiles.requireCurrent().id ?: -1 },
    fetchTimers = {
        when (val result = timers.timers()) {
            is TimerListResult.Loaded -> result.timers
            is TimerListResult.Failed -> emptyList()
        }
    },
    loadBouquetServices = epg::bouquetServices,
    persistBouquet = persistGate::persist,
    shouldSkipReceiverHttp = { hasCache ->
        sessions.status.value.shouldSkipReceiverHttp(hasCache)
    },
    isSessionOffline = {
        sessions.status.value.session == ConnectionStatus.Session.Offline
    },
    loadCachedRoster = epg::cachedBouquetServices,
    loadCachedTimers = { timers.snapshot() }
)

/** Absent key reads as [MULTI_EPG_VISIBLE_MINUTES] and is not written back. */
fun readMultiEpgVisibleMinutes(handle: SavedStateHandle): Int =
    handle.get<Int>(MULTI_EPG_VISIBLE_MINUTES_KEY) ?: MULTI_EPG_VISIBLE_MINUTES

fun writeMultiEpgVisibleMinutes(handle: SavedStateHandle, minutes: Int) {
    handle[MULTI_EPG_VISIBLE_MINUTES_KEY] = minutes
}
