package net.reichholf.dreamdroid.tv.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgGridState
import net.reichholf.dreamdroid.multiepg.MultiEpgNowClock
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.ui.multiepg.MULTI_EPG_VISIBLE_MINUTES
import net.reichholf.dreamdroid.ui.multiepg.newMultiEpgGrid
import net.reichholf.dreamdroid.ui.multiepg.readMultiEpgVisibleMinutes
import net.reichholf.dreamdroid.ui.multiepg.writeMultiEpgVisibleMinutes
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * The TV MultiEPG: the bouquet it shows and the ones it can switch to, the loaded grid,
 * the focused cell, the zoom, the open overlay (detail, timer editor, or bouquet picker),
 * and the set-timer request. [streamingEnabled] and [mutationsBlocked] follow the session.
 */
data class TvMultiEpgUiState(
    val bouquetRef: String = "",
    val bouquetName: String = "",
    val bouquets: List<Service> = emptyList(),
    val grid: MultiEpgGridState = MultiEpgGridState(),
    val visibleMinutes: Int = MULTI_EPG_VISIBLE_MINUTES,
    val selectedServiceRef: String = "",
    val selectedStartSec: Long = 0L,
    val detailEvent: Event? = null,
    val editTimerEvent: Event? = null,
    val pickingBouquet: Boolean = false,
    val settingTimer: Boolean = false,
    val userMessage: UiText? = null,
    val streamingEnabled: Boolean = false,
    val mutationsBlocked: Boolean = false
) {
    val title: UiText
        get() = if (bouquetName.isBlank()) {
            UiText.Resource(R.string.multiepg)
        } else {
            UiText.Raw(bouquetName)
        }

    /** False while an overlay has the D-pad. */
    val gridKeysEnabled: Boolean
        get() = detailEvent == null && editTimerEvent == null && !pickingBouquet
}

/**
 * State of the TV MultiEPG route. The grid loads on [viewModelScope], so it outlives
 * recomposition and configuration changes.
 */
@HiltViewModel
class TvMultiEpgViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val epg: EpgRepository,
    private val timers: TimerRepository,
    private val profiles: ProfileRepository,
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
        TvMultiEpgUiState(visibleMinutes = readMultiEpgVisibleMinutes(savedStateHandle))
    )
    val uiState: StateFlow<TvMultiEpgUiState> = combine(
        _uiState,
        grid.state,
        sessions.status
    ) { state, grid, connection ->
        state.copy(
            grid = grid,
            streamingEnabled = connection.allowsStreaming(),
            mutationsBlocked = connection.blocksMutations
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)

    private var startJob: Job? = null

    /** Loads the bouquet list and the launch bouquet once per ViewModel. */
    fun start(extraRef: String?, extraName: String?) {
        if (startJob != null) {
            return
        }
        startJob = viewModelScope.launch {
            val services = epg.tvBouquets()
            persistGate.knownTabRefs = epg.userBouquetTabRefs(services)
            val bouquets = services.filter { it.reference.isNotBlank() }
            val profile = profiles.requireCurrent()
            val launch = resolveTvMultiEpgLaunchBouquet(
                extraRef = extraRef,
                extraName = extraName,
                defaultRef = profile.defaultBouquetTv.orEmpty(),
                defaultName = profile.defaultBouquetTvName.orEmpty(),
                firstBouquet = bouquets.firstOrNull()
            )
            _uiState.update {
                it.copy(bouquets = bouquets, bouquetRef = launch.first, bouquetName = launch.second)
            }
            grid.replaceAndLoad(launch.first, MultiEpgNowClock.sec())
        }
    }

    fun select(serviceRef: String, startSec: Long) {
        _uiState.update { it.copy(selectedServiceRef = serviceRef, selectedStartSec = startSec) }
    }

    /** Moves the focused cell back onto a bar after the grid changed. */
    fun reconcileSelection() {
        val state = _uiState.value
        val next = reconcileTvMultiEpgSelection(
            grid.state.value.channels,
            state.selectedServiceRef,
            state.selectedStartSec
        ) ?: return
        select(next.first, next.second)
    }

    /** Reloads the bouquet with [nowSec] as the new left edge. */
    fun jumpToNow(nowSec: Long) {
        select("", 0L)
        grid.replaceAndLoad(_uiState.value.bouquetRef, nowSec)
    }

    /** One day back, never before the grid's left edge. [zoomSeconds] is the visible span. */
    fun previousDay(zoomSeconds: Long) {
        val state = grid.state.value
        val target = maxOf(state.originFloorSec, state.anchorSec - MultiEpgWindows.CHUNK_SECONDS)
        focusDay(target, zoomSeconds)
    }

    fun nextDay(zoomSeconds: Long) {
        focusDay(grid.state.value.anchorSec + MultiEpgWindows.CHUNK_SECONDS, zoomSeconds)
    }

    /** Refetches the chunk in view even when Room is fresh. */
    fun refresh() {
        grid.load(grid.state.value.anchorSec, forceRefresh = true, isPull = false)
    }

    fun onVisibleWindow(startSec: Long, endSec: Long) {
        grid.onVisibleWindow(startSec, endSec)
    }

    fun pickBouquet(service: Service) {
        _uiState.update { it.copy(pickingBouquet = false) }
        if (service.reference == _uiState.value.bouquetRef) {
            return
        }
        _uiState.update {
            it.copy(
                bouquetRef = service.reference,
                bouquetName = service.name.ifBlank { service.reference }
            )
        }
        jumpToNow(MultiEpgNowClock.sec())
    }

    fun showDetail(event: Event) {
        _uiState.update { it.copy(detailEvent = event) }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(detailEvent = null) }
    }

    /** Closes the detail and opens the timer editor for [event]. */
    fun editTimer(event: Event) {
        _uiState.update { it.copy(detailEvent = null, editTimerEvent = event) }
    }

    fun dismissTimerEditor() {
        _uiState.update { it.copy(editTimerEvent = null) }
    }

    /** The editor saved a timer: close it and reload so the timer's clock shows. */
    fun onTimerSaved() {
        dismissTimerEditor()
        refresh()
    }

    fun showBouquetPicker() {
        _uiState.update { it.copy(pickingBouquet = true) }
    }

    fun dismissBouquetPicker() {
        _uiState.update { it.copy(pickingBouquet = false) }
    }

    fun onVisibleMinutesChange(minutes: Int) {
        _uiState.update { it.copy(visibleMinutes = minutes) }
        writeMultiEpgVisibleMinutes(savedStateHandle, minutes)
    }

    /** Adds a timer for [event] by its event id. The receiver's answer is the user message. */
    fun setTimer(event: Event) {
        if (_uiState.value.settingTimer) {
            return
        }
        _uiState.update { it.copy(settingTimer = true) }
        viewModelScope.launch {
            val response = timers.addByEvent(event)
            _uiState.update {
                it.copy(settingTimer = false, userMessage = response.userMessageText())
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun focusDay(targetSec: Long, zoomSeconds: Long) {
        grid.focusAt(targetSec)
        grid.onVisibleWindow(targetSec, targetSec + zoomSeconds)
    }
}

/**
 * The focused cell after [channels] changed, or null when it still points at a bar.
 * A missing channel falls back to the first one; a missing bar to the bar covering
 * [startSec], then to the channel's first bar.
 */
fun reconcileTvMultiEpgSelection(
    channels: List<MultiEpgChannel>,
    serviceRef: String,
    startSec: Long
): Pair<String, Long>? {
    if (channels.isEmpty()) {
        return null
    }
    val current = channels.find { it.serviceRef == serviceRef }
    if (current == null) {
        val first = channels.first()
        return first.serviceRef to (first.bars.firstOrNull()?.startSec ?: 0L)
    }
    if (current.bars.isEmpty() || current.bars.any { it.startSec == startSec }) {
        return null
    }
    val overlap = current.bars.firstOrNull { bar ->
        bar.startSec <= startSec && bar.endSec > startSec
    }
    return serviceRef to (overlap?.startSec ?: current.bars.first().startSec)
}
