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
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.AppSettings
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.data.TimerListResult
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.multiepg.MultiEpgGrid
import net.reichholf.dreamdroid.multiepg.MultiEpgGridState
import net.reichholf.dreamdroid.multiepg.MultiEpgPersistGate
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

const val MULTI_EPG_VISIBLE_MINUTES_KEY: String = "multi_epg_visible_minutes"

/**
 * The phone MultiEPG destination: the bouquet it shows, the loaded grid, the zoom, and the
 * text size and picon settings.
 */
data class MultiEpgUiState(
    val bouquetName: String = "",
    val grid: MultiEpgGridState = MultiEpgGridState(),
    val visibleMinutes: Int = MULTI_EPG_VISIBLE_MINUTES,
    val textSize: MultiEpgTextSize = MultiEpgTextSize.DEFAULT,
    val piconsEnabled: Boolean = false
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
    epg: EpgRepository,
    private val services: ServiceRepository,
    timers: TimerRepository,
    private val profiles: ProfileRepository,
    sessions: SessionConnectionHolder,
    settings: SettingsRepository
) : ViewModel() {
    private val persistGate = epg.multiEpgPersistGate()
    private val grid = newMultiEpgGrid(
        viewModelScope,
        epg,
        services,
        timers,
        profiles,
        sessions,
        persistGate
    )
    private val _uiState = MutableStateFlow(
        MultiEpgUiState(visibleMinutes = readMultiEpgVisibleMinutes(savedStateHandle))
            .withSettings(settings.current())
    )
    val uiState: StateFlow<MultiEpgUiState> = combine(
        _uiState,
        grid.state,
        settings.settings
    ) { state, grid, appSettings ->
        state.copy(grid = grid).withSettings(appSettings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)

    private var bouquetRef: String = ""
    private var tabRootRef: String? = null
    private var appliedKey: Triple<Int, String, String>? = null
    private var inFlightKey: Triple<Int, String, String>? = null
    private var loadGeneration: Int = 0
    private var loadJob: Job? = null

    /**
     * Loads [bouquetRef] at [anchorSec] when [remountEpoch], the bouquet, or its
     * [tabRootRef] changed. A repeat call for the same triple leaves the in-flight or
     * finished load alone.
     */
    fun ensureLoaded(
        remountEpoch: Int,
        bouquetRef: String,
        bouquetName: String,
        anchorSec: Long,
        tabRootRef: String? = null
    ) {
        _uiState.update { it.copy(bouquetName = bouquetName) }
        this.bouquetRef = bouquetRef
        this.tabRootRef = tabRootRef
        val key = Triple(remountEpoch, bouquetRef, tabRootRef.orEmpty())
        if (appliedKey == key || inFlightKey == key) {
            return
        }
        val generation = ++loadGeneration
        inFlightKey = key
        loadJob?.cancel()
        val profileId = profiles.current.value?.id
        loadJob = viewModelScope.launch {
            try {
                persistGate.knowTabs(profileId, services.hubTabStripRefs(profileId))
                if (generation != loadGeneration) {
                    return@launch
                }
                appliedKey = key
                grid.replaceAndLoad(bouquetRef, anchorSec, tabRootRef)
            } finally {
                if (generation == loadGeneration) {
                    inFlightKey = null
                }
            }
        }
    }

    /** Reloads the bouquet with [nowSec] as the new left edge. */
    fun jumpToNow(nowSec: Long) {
        grid.replaceAndLoad(bouquetRef, nowSec, tabRootRef)
    }

    fun previousDay() {
        val state = grid.state.value
        grid.focusAt(maxOf(state.originFloorSec, state.anchorSec - MultiEpgWindows.CHUNK_SECONDS))
    }

    fun nextDay() {
        grid.focusAt(grid.state.value.anchorSec + MultiEpgWindows.CHUNK_SECONDS)
    }

    /** Pull-to-refresh: refetches the chunks in view even when Room is fresh. */
    fun refresh() {
        grid.refresh(isPull = true)
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
    services: ServiceRepository,
    timers: TimerRepository,
    profiles: ProfileRepository,
    sessions: SessionConnectionHolder,
    persistGate: MultiEpgPersistGate
): MultiEpgGrid = MultiEpgGrid(
    sync = epg.multiEpgSync,
    scope = scope,
    profileId = { profiles.current.value?.id ?: -1 },
    // The grid only asks for the timers once it decided to ask the receiver. Both the
    // timers and the roster resolve the profile the load captured, not the current one.
    fetchTimers = { profileId ->
        when (val result = timers.timers(profileId, forceRefresh = true).last()) {
            is TimerListResult.Loaded -> result.timers
            is TimerListResult.Failed -> emptyList()
        }
    },
    loadBouquetServices = services::bouquetServices,
    persistBouquet = persistGate::persist,
    shouldSkipReceiverHttp = { hasCache ->
        sessions.status.value.shouldSkipReceiverHttp(hasCache)
    },
    isSessionOffline = {
        sessions.status.value.session == ConnectionStatus.Session.Offline
    },
    loadCachedRoster = services::cachedBouquetServices,
    loadCachedTimers = { profileId -> timers.snapshot(profileId) }
)

/** Absent key reads as [MULTI_EPG_VISIBLE_MINUTES] and is not written back. */
fun readMultiEpgVisibleMinutes(handle: SavedStateHandle): Int =
    handle.get<Int>(MULTI_EPG_VISIBLE_MINUTES_KEY) ?: MULTI_EPG_VISIBLE_MINUTES

fun writeMultiEpgVisibleMinutes(handle: SavedStateHandle, minutes: Int) {
    handle[MULTI_EPG_VISIBLE_MINUTES_KEY] = minutes
}

private fun MultiEpgUiState.withSettings(settings: AppSettings): MultiEpgUiState = copy(
    textSize = MultiEpgTextSize.fromPref(settings.multiEpgTextSize),
    piconsEnabled = settings.picons
)
