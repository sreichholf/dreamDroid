package net.reichholf.dreamdroid.ui.current

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * The hub's now-playing strip. [current] is the last `/web/getcurrent` that named a service
 * for the active profile; [shown] hides it while the session is Offline. [enabled] follows
 * the now-playing strip setting.
 */
data class HubNowPlayingUiState(
    val enabled: Boolean = true,
    val current: CurrentService? = null,
    val ready: Boolean = false,
    val sessionOffline: Boolean = false,
    val streamBlocked: Boolean = false,
    val sheetOpen: Boolean = false,
    val stream: CurrentServiceStream? = null,
    val userMessage: UiText? = null
) {
    val shown: CurrentService?
        get() = current.takeUnless { sessionOffline }

    /** Offline, the strip reports the connection, not a programme. */
    val label: UiText
        get() = UiText.Resource(
            if (sessionOffline) R.string.connection else R.string.current_service
        )

    val headline: UiText
        get() {
            if (!ready) {
                return UiText.Resource(R.string.loading)
            }
            val name = shown?.service?.name.orEmpty()
            val title = shown?.now?.title.orEmpty()
            return when {
                name.isNotEmpty() && title.isNotEmpty() -> UiText.Raw("$name · $title")
                name.isNotEmpty() -> UiText.Raw(name)
                title.isNotEmpty() -> UiText.Raw(title)
                sessionOffline -> UiText.Resource(R.string.session_offline)
                else -> UiText.Resource(R.string.not_available)
            }
        }
}

/**
 * `/web/getcurrent` for [HubNowPlaying], on the hub back-stack entry, so re-entering the
 * hub paints the last-good service at once. Polling runs only while [poll] is collected
 * from composition; each load runs on [viewModelScope], and a newer load cancels an older
 * one.
 */
@HiltViewModel
class HubNowPlayingViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val receiver: ReceiverRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder,
    settings: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HubNowPlayingUiState(sheetOpen = savedStateHandle[KEY_SHEET_OPEN] ?: false)
    )
    val uiState: StateFlow<HubNowPlayingUiState> = _uiState.asStateFlow()

    private var lastGood: CurrentService? = null
    private var lastGoodProfileId: Int? = null
    private var lastSuccess: TimeSource.Monotonic.ValueTimeMark? = null
    private var loadJob: Job? = null
    private var streamJob: Job? = null
    private var handledReloadEpoch = 0

    init {
        viewModelScope.launch {
            settings.settings.map { it.nowPlayingStrip }.distinctUntilChanged().collect { on ->
                _uiState.update { it.copy(enabled = on) }
            }
        }
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update {
                    it.copy(
                        sessionOffline = status.session == ConnectionStatus.Session.Offline,
                        streamBlocked = status.blocksMutations
                    )
                }
            }
        }
    }

    /**
     * Paints the last-good service of the active profile, then reloads every [POLL_MS].
     * Starts over on each session or profile change. Runs until cancelled.
     */
    suspend fun poll() {
        combine(
            sessions.status.map { it.session },
            profiles.current.map { it?.id }
        ) { session, profileId -> session to profileId }
            .distinctUntilChanged()
            .collectLatest { (session, _) -> pollSession(session) }
    }

    /** [epoch] is the hub's saved zap counter; re-entering the hub must not reload again. */
    fun onReloadEpoch(epoch: Int) {
        if (epoch <= handledReloadEpoch) {
            return
        }
        handledReloadEpoch = epoch
        reload()
    }

    fun reload() {
        if (sessions.status.value.session == ConnectionStatus.Session.Offline) {
            _uiState.update { it.copy(ready = true) }
            return
        }
        val profileId = currentProfileId()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val next = receiver.currentService().value
            if (next != null && !next.isEmpty()) {
                lastGood = next
                lastGoodProfileId = profileId
                lastSuccess = TimeSource.Monotonic.markNow()
            }
            _uiState.update { it.copy(current = visible(), ready = true) }
        }
    }

    fun openSheet() {
        setSheetOpen(true)
    }

    /** Closing the sheet asks the receiver again. */
    fun closeSheet() {
        setSheetOpen(false)
        reload()
    }

    /** Streams the shown service once the receiver can ([ReceiverRepository.liveStream]). */
    fun stream() {
        val shown = _uiState.value.shown
        val service = shown?.service
        if (!currentServiceCanStream(shown) || service == null) {
            return
        }
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _uiState.update {
                when (val stream = receiver.liveStream(service.reference)) {
                    is LiveStream.Ready ->
                        it.copy(stream = CurrentServiceStream(service.name, stream))

                    is LiveStream.Failed -> it.copy(userMessage = stream.message)
                }
            }
        }
    }

    fun onStreamStarted() {
        _uiState.update { it.copy(stream = null) }
    }

    /** No app could play the stream. */
    fun onStreamFailed() {
        _uiState.update { it.copy(userMessage = UiText.Resource(R.string.missing_stream_player)) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private suspend fun pollSession(session: ConnectionStatus.Session?) {
        val painted = visible()
        val offline = session == ConnectionStatus.Session.Offline
        _uiState.update { it.copy(current = painted, ready = offline || painted != null) }
        if (offline) {
            return
        }
        if (session != ConnectionStatus.Session.Online) {
            receiver.awaitProfileCheck()
        }
        val since = lastSuccess
        if (painted != null && since != null) {
            delay(POLL_MS.milliseconds - since.elapsedNow())
        }
        while (true) {
            reload()
            delay(POLL_MS)
        }
    }

    private fun setSheetOpen(open: Boolean) {
        savedStateHandle[KEY_SHEET_OPEN] = open
        _uiState.update { it.copy(sheetOpen = open) }
    }

    private fun visible(): CurrentService? =
        lastGood.takeIf { lastGoodProfileId == currentProfileId() }

    private fun currentProfileId(): Int = profiles.current.value?.id ?: -1

    private companion object {
        const val POLL_MS = 30_000L
        const val KEY_SHEET_OPEN = "now_playing_sheet_open"
    }
}
