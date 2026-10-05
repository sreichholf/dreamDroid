package net.reichholf.dreamdroid.ui.zap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.data.ServiceListLoad
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** Something the destination does outside this ViewModel, then reports with `onEffectHandled`. */
sealed interface ZapEffect {
    /** Open the bouquet picker; its answer comes back through `onBouquetPicked`. */
    data object PickBouquet : ZapEffect

    /** Play [stream] of [service]. */
    data class Stream(val service: Service, val stream: LiveStream.Ready) : ZapEffect
}

/**
 * The zap grid of one bouquet. [emptyMessage] shows instead of the grid while [items] is
 * empty. [scrollEpoch] grows when another bouquet was picked, so the grid goes to the top.
 */
data class ZapUiState(
    val bouquetName: String = "",
    val items: List<Service> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null,
    val scrollEpoch: Int = 0,
    val zapBlocked: Boolean = false,
    val effect: ZapEffect? = null,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = when {
            refreshing -> UiText.Resource(R.string.loading)
            bouquetName.isNotEmpty() -> UiText.Raw(bouquetName)
            else -> UiText.Resource(R.string.app_name)
        }
}

/**
 * The zap grid. Starts on the saved bouquet, else the profile's default TV bouquet, else
 * opens the bouquet picker. Loads and zaps run on [viewModelScope], so leaving the
 * destination does not cancel them.
 */
@HiltViewModel
class ZapViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val services: ServiceRepository,
    private val receiver: ReceiverRepository,
    private val sessions: SessionConnectionHolder,
    profiles: ProfileRepository
) : ViewModel() {
    private var saved: ZapNavSaved
    private var loadJob: Job? = null
    private var zapJob: Job? = null
    private var streamJob: Job? = null

    private val _uiState: MutableStateFlow<ZapUiState>
    val uiState: StateFlow<ZapUiState>

    init {
        val profile = profiles.requireCurrent()
        saved = readZapNavSaved(
            savedStateHandle,
            defaultBouquetRef = profile.defaultBouquetTv.orEmpty(),
            defaultBouquetName = profile.defaultBouquetTvName.orEmpty()
        )
        _uiState = MutableStateFlow(ZapUiState(bouquetName = saved.bouquetName))
        uiState = _uiState.asStateFlow()
        viewModelScope.launch {
            sessions.status.collect { status ->
                _uiState.update { it.copy(zapBlocked = status.blocksMutations) }
            }
        }
        reload()
    }

    /**
     * Loads the bouquet, or opens the picker when there is none and it is not open yet. Room
     * paints first unless [forceRefresh].
     */
    fun reload(forceRefresh: Boolean = false) {
        if (saved.bouquetRef.isEmpty()) {
            if (!saved.waitingForPicker) {
                pickBouquet()
            }
            return
        }
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.items.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
        val bouquetRef = saved.bouquetRef
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            services.services(bouquetRef, forceRefresh).collect { load -> apply(load) }
        }
    }

    private fun apply(load: ServiceListLoad) {
        _uiState.update {
            when (load) {
                is ServiceListLoad.Services -> {
                    val rows = ZapListMapper.rowsFrom(load.services)
                    it.copy(
                        refreshing = false,
                        items = rows,
                        emptyMessage = if (rows.isEmpty()) {
                            UiText.Resource(R.string.no_list_item)
                        } else {
                            null
                        }
                    )
                }

                is ServiceListLoad.Failed -> it.copy(
                    refreshing = false,
                    items = emptyList(),
                    emptyMessage = load.error.contentErrorText()
                )
            }
        }
    }

    /** Zaps to [service] and reports the receiver's answer. Ignored while blocked. */
    fun zap(service: Service) {
        if (sessions.status.value.blocksMutations) {
            return
        }
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            val message = receiver.zap(service.reference).userMessageText()
            _uiState.update { it.copy(userMessage = message) }
        }
    }

    /** Streams [service] once the receiver can ([ReceiverRepository.liveStream]). */
    fun stream(service: Service) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            when (val stream = receiver.liveStream(service.reference)) {
                is LiveStream.Ready ->
                    _uiState.update { it.copy(effect = ZapEffect.Stream(service, stream)) }

                is LiveStream.Failed -> _uiState.update { it.copy(userMessage = stream.message) }
            }
        }
    }

    fun pickBouquet() {
        persist(saved.copy(waitingForPicker = true))
        _uiState.update { it.copy(effect = ZapEffect.PickBouquet) }
    }

    /** The picker answered with [bouquet]; another bouquet than before scrolls to the top. */
    fun onBouquetPicked(bouquet: Service) {
        val changed = bouquet.reference != saved.bouquetRef
        persist(
            if (changed) {
                ZapNavSaved(bouquet.reference, bouquet.name, waitingForPicker = false)
            } else {
                saved.copy(waitingForPicker = false)
            }
        )
        _uiState.update {
            it.copy(
                bouquetName = saved.bouquetName,
                scrollEpoch = if (changed) it.scrollEpoch + 1 else it.scrollEpoch
            )
        }
        reload()
    }

    /** The picker closed without a bouquet. The next reload without one opens it again. */
    fun onBouquetPickCancelled() {
        persist(saved.copy(waitingForPicker = false))
        _uiState.update {
            if (it.items.isEmpty()) {
                it.copy(emptyMessage = UiText.Resource(R.string.no_list_item))
            } else {
                it
            }
        }
    }

    fun onEffectHandled() {
        _uiState.update { it.copy(effect = null) }
    }

    /** No app could play the stream. */
    fun onStreamFailed() {
        _uiState.update { it.copy(userMessage = UiText.Resource(R.string.missing_stream_player)) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun persist(next: ZapNavSaved) {
        saved = next
        next.writeTo(savedStateHandle)
    }
}
