package net.reichholf.dreamdroid.ui.current

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** Play [stream] of the service [name], then report with `onStreamStarted`. */
data class CurrentServiceStream(val name: String, val stream: LiveStream.Ready)

/**
 * The Current service screen. [current] is the last `/web/getcurrent` that named a service
 * for the active profile. Until the first answer, [ready] is false and the fields say
 * Loading; a failure without a last-good service shows the screen empty.
 */
data class CurrentServiceUiState(
    val current: CurrentService? = null,
    val ready: Boolean = false,
    val refreshing: Boolean = false,
    val piconsEnabled: Boolean = false,
    val streamBlocked: Boolean = false,
    val stream: CurrentServiceStream? = null,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(if (refreshing) R.string.loading else R.string.current_service)

    val canStream: Boolean
        get() = currentServiceCanStream(current)
}

/**
 * Loads `/web/getcurrent` and keeps the last-good answer of the active profile, also in the
 * [SavedStateHandle]. A profile switch drops what belonged to the other profile and loads
 * again. The EPG sheet of the now and next event is the destination's
 * [net.reichholf.dreamdroid.ui.epg.EpgEventDetailViewModel].
 */
@HiltViewModel
class CurrentServiceViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val receiver: ReceiverRepository,
    private val profiles: ProfileRepository,
    sessions: SessionConnectionHolder,
    settings: SettingsRepository
) : ViewModel() {
    private var activeProfileId = currentProfileId()
    private var lastGood: CurrentService? = null
    private var lastGoodProfileId: Int? = null
    private var loadJob: Job? = null
    private var streamJob: Job? = null

    private val _uiState: MutableStateFlow<CurrentServiceUiState>
    val uiState: StateFlow<CurrentServiceUiState>

    init {
        val saved = readCurrentServiceSaved(savedStateHandle, activeProfileId).current
            ?.takeUnless { it.isEmpty() }
        if (saved != null) {
            lastGood = saved
            lastGoodProfileId = activeProfileId
        }
        _uiState = MutableStateFlow(CurrentServiceUiState(current = saved, ready = saved != null))
        uiState = _uiState.asStateFlow()
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update { it.copy(streamBlocked = status.blocksMutations) }
            }
        }
        viewModelScope.launch {
            settings.settings.map { it.picons }.distinctUntilChanged().collect { picons ->
                _uiState.update { it.copy(piconsEnabled = picons) }
            }
        }
        viewModelScope.launch {
            profiles.current.map { it?.id ?: -1 }.distinctUntilChanged().collect(::onProfile)
        }
        if (saved == null) {
            reload()
        }
    }

    fun reload() {
        val profileId = activeProfileId
        _uiState.update { it.copy(refreshing = true) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val next = receiver.currentService().value
            if (next != null && !next.isEmpty()) {
                lastGood = next
                lastGoodProfileId = profileId
                CurrentServiceSaved(next, profileId).writeTo(savedStateHandle)
            }
            _uiState.update { it.copy(current = visible(), ready = true, refreshing = false) }
        }
    }

    /** Streams the service on screen once the receiver can ([ReceiverRepository.liveStream]). */
    fun stream() {
        val state = _uiState.value
        val service = state.current?.service
        if (!state.ready || !state.canStream || service == null) {
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

    private fun onProfile(profileId: Int) {
        if (profileId == activeProfileId) {
            return
        }
        activeProfileId = profileId
        loadJob?.cancel()
        val shown = visible()
        CurrentServiceSaved(shown, profileId.takeIf { shown != null })
            .writeTo(savedStateHandle)
        _uiState.update { it.copy(current = shown, ready = shown != null, refreshing = false) }
        if (shown == null) {
            reload()
        }
    }

    private fun visible(): CurrentService? =
        lastGood.takeIf { lastGoodProfileId == activeProfileId }

    private fun currentProfileId(): Int = profiles.current.value?.id ?: -1
}
