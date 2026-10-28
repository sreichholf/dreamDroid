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
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.multiepg.MultiEpgChannel
import net.reichholf.dreamdroid.multiepg.MultiEpgChannelKey
import net.reichholf.dreamdroid.multiepg.MultiEpgGridState
import net.reichholf.dreamdroid.multiepg.MultiEpgNowClock
import net.reichholf.dreamdroid.multiepg.MultiEpgTextSize
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.ui.multiepg.MULTI_EPG_VISIBLE_MINUTES
import net.reichholf.dreamdroid.ui.multiepg.newMultiEpgGrid
import net.reichholf.dreamdroid.ui.multiepg.readMultiEpgVisibleMinutes
import net.reichholf.dreamdroid.ui.multiepg.writeMultiEpgVisibleMinutes
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** Play [stream] of [service], titled by the event [title], as a channel of [bouquetRef]. */
data class TvMultiEpgStream(
    val title: String,
    val service: ServiceNowNext,
    val bouquetRef: String,
    val stream: LiveStream.Ready
)

/**
 * The TV MultiEPG: the bouquet it shows and the ones it can switch to, the loaded grid,
 * the focused cell, the zoom, the open overlay (detail, timer editor, or bouquet picker),
 * the set-timer request, and a [stream] for the player. [streamingEnabled] and
 * [mutationsBlocked] follow the session, [textSize] and [piconsEnabled] the settings.
 */
data class TvMultiEpgUiState(
    val bouquetRef: String = "",
    val bouquetName: String = "",
    val bouquets: List<Service> = emptyList(),
    val grid: MultiEpgGridState = MultiEpgGridState(),
    val visibleMinutes: Int = MULTI_EPG_VISIBLE_MINUTES,
    val selectedKey: MultiEpgChannelKey? = null,
    val selectedStartSec: Long = 0L,
    val detailEvent: Event? = null,
    val editTimerEvent: Event? = null,
    val pickingBouquet: Boolean = false,
    val settingTimer: Boolean = false,
    val userMessage: UiText? = null,
    val stream: TvMultiEpgStream? = null,
    val streamingEnabled: Boolean = false,
    val mutationsBlocked: Boolean = false,
    val textSize: MultiEpgTextSize = MultiEpgTextSize.DEFAULT,
    val piconsEnabled: Boolean = false
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
 * State of the TV hub's Guide. The grid loads on [viewModelScope], so it outlives
 * recomposition, configuration changes and switching to another hub section.
 */
@HiltViewModel
class TvMultiEpgViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    epg: EpgRepository,
    private val services: ServiceRepository,
    private val timers: TimerRepository,
    private val profiles: ProfileRepository,
    private val receiver: ReceiverRepository,
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
        TvMultiEpgUiState(
            visibleMinutes = readMultiEpgVisibleMinutes(savedStateHandle),
            textSize = MultiEpgTextSize.fromPref(settings.current().multiEpgTextSize),
            piconsEnabled = settings.current().picons
        )
    )
    val uiState: StateFlow<TvMultiEpgUiState> = combine(
        _uiState,
        grid.state,
        sessions.status,
        settings.settings
    ) { state, grid, connection, appSettings ->
        state.copy(
            grid = grid,
            streamingEnabled = connection.allowsStreaming(),
            mutationsBlocked = connection.blocksMutations,
            textSize = MultiEpgTextSize.fromPref(appSettings.multiEpgTextSize),
            piconsEnabled = appSettings.picons
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value)

    private var startJob: Job? = null
    private var streamJob: Job? = null

    /**
     * Shows the bouquet [ref] named [name]: the first call loads the bouquet list with it as
     * the launch bouquet, later calls switch to it ([pickBouquet]).
     */
    fun open(ref: String?, name: String?) {
        val job = startJob
        when {
            job == null -> start(ref, name)

            job.isActive -> {
                job.cancel()
                startJob = null
                start(ref, name)
            }

            ref != null -> pickBouquet(Service(ref, name.orEmpty()))
        }
    }

    /** Loads the bouquet list and the launch bouquet. */
    private fun start(extraRef: String?, extraName: String?) {
        // No profile: start nothing. startJob stays null, so an open() that comes later can
        // load; the host does not re-open on its own when a profile appears.
        val profile = profiles.current.value ?: return
        startJob = viewModelScope.launch {
            val tvBouquets = services.tvBouquets()
            persistGate.knownTabRefs = services.userBouquetTabs(tvBouquets).map { it.reference }
            val bouquets = tvBouquets.filter { it.reference.isNotBlank() }
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

    fun select(key: MultiEpgChannelKey?, startSec: Long) {
        _uiState.update { it.copy(selectedKey = key, selectedStartSec = startSec) }
    }

    /** Moves the focused cell back onto a bar after the grid changed. */
    fun reconcileSelection() {
        val state = _uiState.value
        val next = reconcileTvMultiEpgSelection(
            grid.state.value.channels,
            state.selectedKey,
            state.selectedStartSec
        ) ?: return
        select(next.first, next.second)
    }

    /** Reloads the bouquet with [nowSec] as the new left edge. */
    fun jumpToNow(nowSec: Long) {
        select(null, 0L)
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

    /** Refetches the chunks in view even when Room is fresh. */
    fun refresh() {
        grid.refresh()
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

    /**
     * Streams the service of [event] once the receiver can ([ReceiverRepository.liveStream]);
     * otherwise the receiver's answer is the user message.
     */
    fun stream(event: Event) {
        val bouquetRef = _uiState.value.bouquetRef
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            when (val stream = receiver.liveStream(event.serviceReference)) {
                is LiveStream.Ready -> _uiState.update {
                    it.copy(
                        stream = TvMultiEpgStream(
                            title = event.title,
                            service = ServiceNowNext(
                                serviceReference = event.serviceReference,
                                serviceName = event.serviceName
                            ),
                            bouquetRef = bouquetRef,
                            stream = stream
                        )
                    )
                }

                is LiveStream.Failed -> _uiState.update { it.copy(userMessage = stream.message) }
            }
        }
    }

    /** The host handed [TvMultiEpgUiState.stream] to the player. */
    fun onStreamStarted() {
        _uiState.update { it.copy(stream = null) }
    }

    /** No app on the device plays the stream. */
    fun onMissingStreamPlayer() {
        _uiState.update { it.copy(userMessage = UiText.Resource(R.string.missing_stream_player)) }
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
 * A row that disappeared falls back to another row of the same service (its occurrence
 * vanished), then to the first row; a missing bar to the bar covering [startSec], then
 * to the row's first bar.
 */
fun reconcileTvMultiEpgSelection(
    channels: List<MultiEpgChannel>,
    selectedKey: MultiEpgChannelKey?,
    startSec: Long
): Pair<MultiEpgChannelKey, Long>? {
    if (channels.isEmpty()) {
        return null
    }
    val current = selectedKey?.let { key ->
        channels.firstOrNull { it.key == key }
            ?: channels.firstOrNull { it.serviceRef == key.serviceRef }
    }
    if (current == null) {
        val first = channels.first()
        return first.key to (first.bars.firstOrNull()?.startSec ?: 0L)
    }
    if (current.bars.isEmpty() || current.bars.any { it.startSec == startSec }) {
        return if (current.key == selectedKey) null else current.key to startSec
    }
    val overlap = current.bars.firstOrNull { bar ->
        bar.startSec <= startSec && bar.endSec > startSec
    }
    return current.key to (overlap?.startSec ?: current.bars.first().startSec)
}
