package net.reichholf.dreamdroid.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.Serializable
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BouquetListLoad
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.Bouquets
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * Zap list, zap position, and now/next for the player overlay, with their loads.
 * Scoped to [net.reichholf.dreamdroid.activities.VideoActivity] so a recreate keeps
 * the loaded list. [VideoOverlayController] observes [uiState] and keeps libVLC and
 * the views.
 */
@HiltViewModel
class VideoPlaybackViewModel @Inject constructor(
    private val services: ServiceRepository,
    private val receiver: ReceiverRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder
) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoPlaybackUiState())
    val uiState: StateFlow<VideoPlaybackUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var bouquetJob: Job? = null
    private var zapJob: Job? = null
    private var sleepJob: Job? = null

    /** Returns whether the title or a ref changed. */
    fun applyExtras(
        title: String?,
        serviceRef: String?,
        bouquetRef: String?,
        info: Serializable?
    ): Boolean {
        val before = _uiState.value
        val after = before.withExtras(title, serviceRef, bouquetRef, info)
        _uiState.value = after
        if (after.movie != null) {
            bouquetJob?.cancel()
            bouquetJob = null
        } else {
            loadBouquetBar()
        }
        return before.title != after.title ||
            before.serviceRef != after.serviceRef ||
            before.bouquetRef != after.bouquetRef
    }

    fun zapTo(row: ServiceNowNext) {
        _uiState.update { it.zappedTo(row) }
    }

    /** Moves the zap position one row; returns false when the list has no neighbour. */
    fun step(forward: Boolean): Boolean {
        val row = _uiState.value.neighbour(forward) ?: return false
        zapTo(row)
        return true
    }

    /** Returns false when [ref] is empty or already the bouquet. */
    fun selectBouquet(ref: String): Boolean {
        if (ref.isEmpty() || ref == _uiState.value.bouquetRef) {
            return false
        }
        _uiState.update { it.copy(bouquetRef = ref) }
        return true
    }

    fun reload() {
        val bouquetRef = _uiState.value.bouquetRef
        if (bouquetRef.isNullOrEmpty()) {
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            profiles.awaitLoaded()
            val response = services.bouquetNowNext(bouquetRef)
            val rows = response.value
            if (rows == null) {
                showMessage(response.error.contentErrorText())
                return@launch
            }
            _uiState.update { it.withServices(rows) }
        }
    }

    fun cancelReload() {
        loadJob?.cancel()
        loadJob = null
    }

    /**
     * Sets [VideoPlaybackUiState.stream] to the zap position once the receiver can stream it
     * ([ReceiverRepository.liveStream]). A failed zap shows a message and streams nothing.
     */
    fun streamCurrent() {
        val ref = _uiState.value.serviceRef ?: return
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            profiles.awaitLoaded()
            when (val stream = receiver.liveStream(ref)) {
                is LiveStream.Ready -> _uiState.update { it.copy(stream = stream) }
                is LiveStream.Failed -> showMessage(stream.message)
            }
        }
    }

    /** The overlay started [VideoPlaybackUiState.stream]. */
    fun onStreamStarted() {
        _uiState.update { it.copy(stream = null) }
    }

    /** Counts [minutes] down to [SleepTimer.Expired]; 0 turns the timer off. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimer = SleepTimer.Off) }
            showMessage(UiText.Resource(R.string.sleep_timer_cancelled))
            return
        }
        showMessage(UiText.Resource(R.string.sleep_timer_set, listOf(minutes)))
        sleepJob = viewModelScope.launch {
            for (left in minutes downTo 1) {
                _uiState.update { it.copy(sleepTimer = SleepTimer.Running(left)) }
                delay(MINUTE_MS)
            }
            _uiState.update { it.copy(sleepTimer = SleepTimer.Expired) }
        }
    }

    fun showMessage(message: UiText) {
        _uiState.update { it.copy(userMessage = message) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /** Cache first, then the receiver. One load per ViewModel; a recording clears it. */
    private fun loadBouquetBar() {
        if (bouquetJob != null) {
            return
        }
        bouquetJob = viewModelScope.launch {
            // A restore after process death can land here before the active profile is read.
            profiles.awaitLoaded()
            val excluded = services.excludedTabRefs
            val cached = services.cachedBouquets()
            val hasStrip = cached.tv.isNotEmpty() || cached.radio.isNotEmpty()
            if (hasStrip) {
                publishBouquets(overlayBouquets(cached.tv, cached.radio, excluded))
            }
            if (sessions.status.value.shouldSkipReceiverHttp(hasStrip)) {
                return@launch
            }
            val painted = when (val load = services.bouquets()) {
                is BouquetListLoad.Loaded -> load.bouquets
                is BouquetListLoad.Failed -> Bouquets()
            }
            publishBouquets(overlayBouquets(painted.tv, painted.radio, excluded))
        }
    }

    private fun publishBouquets(items: List<Service>) {
        _uiState.update { state ->
            if (state.movie != null) state else state.copy(bouquets = items)
        }
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
