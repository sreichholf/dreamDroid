package net.reichholf.dreamdroid.ui.epg

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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.EventListLoad
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.nav.Epg
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * Bouquet list EPG. [timeSec] is null until the first bind seeds it. [scrollToTop] and
 * [openPicker] are requests the destination carries out and then reports back.
 */
data class EpgBouquetUiState(
    val bouquetRef: String = "",
    val bouquetName: String = "",
    val timeSec: Int? = null,
    val waitingForPicker: Boolean = false,
    val events: List<Event> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null,
    val scrollToTop: Boolean = false,
    val openPicker: Boolean = false,
    val piconsEnabled: Boolean = false
) {
    val title: UiText
        get() = when {
            refreshing -> UiText.Resource(R.string.loading)
            bouquetName.isNotEmpty() -> UiText.Raw(bouquetName)
            else -> UiText.Resource(R.string.epg)
        }
}

/**
 * Owns bouquet identity, time, and the list load of the list EPG destination. Identity and
 * time live in the [SavedStateHandle]. The load runs on [viewModelScope], so leaving the
 * destination does not cancel it. A session change reloads once the destination has bound.
 */
@HiltViewModel
class EpgBouquetViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val epg: EpgRepository,
    sessions: SessionConnectionHolder,
    settings: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        readEpgBouquetNavSaved(savedStateHandle).let { saved ->
            EpgBouquetUiState(
                bouquetRef = saved.bouquetRef,
                bouquetName = saved.bouquetName,
                timeSec = saved.timeSec?.toInt(),
                waitingForPicker = saved.waitingForPicker
            )
        }
    )
    val uiState: StateFlow<EpgBouquetUiState> = _uiState.asStateFlow()

    private var seenEpoch: Int? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.picons }.distinctUntilChanged().collect { on ->
                _uiState.update { it.copy(piconsEnabled = on) }
            }
        }
        viewModelScope.launch {
            sessions.status.map { it.session }.distinctUntilChanged().drop(1).collect {
                if (seenEpoch != null) {
                    reload()
                }
            }
        }
    }

    /**
     * The destination is shown for [route]: bind it and reload. The first bind keeps a
     * restored snapshot and seeds what is missing from the route, or [nowSec]. A later
     * [remountEpoch] resets bouquet and time to the route's; waiting for the picker is
     * kept. A picked bouquet stays active over the route's until then.
     */
    fun onShown(route: Epg, remountEpoch: Int, nowSec: Int) {
        ensureEpoch(remountEpoch, route.serviceRef, route.serviceName, route.timeOrNull(), nowSec)
        applyLeaf(route.serviceRef, route.serviceName)
        reload()
    }

    fun reload(forceRefresh: Boolean = false) {
        val state = _uiState.value
        if (state.bouquetRef.isEmpty()) {
            if (!state.waitingForPicker) {
                pickBouquet()
            }
            return
        }
        val atSec = state.timeSec ?: return
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.events.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            epg.bouquetEvents(state.bouquetRef, atSec.toLong(), forceRefresh).collect(::apply)
        }
    }

    fun onInstantSet(newTimeSec: Int) {
        if (newTimeSec == _uiState.value.timeSec) {
            return
        }
        persist { it.copy(timeSec = newTimeSec.toLong()) }
        reload()
    }

    fun pickBouquet() {
        persist { it.copy(waitingForPicker = true) }
        _uiState.update { it.copy(openPicker = true) }
    }

    fun onPickerOpened() {
        _uiState.update { it.copy(openPicker = false) }
    }

    fun onBouquetPicked(reference: String, name: String) {
        if (reference != _uiState.value.bouquetRef) {
            persist {
                it.copy(bouquetRef = reference, bouquetName = name, waitingForPicker = false)
            }
            _uiState.update { it.copy(scrollToTop = true) }
        } else {
            persist { it.copy(waitingForPicker = false) }
        }
        reload()
    }

    fun onScrolledToTop() {
        _uiState.update { it.copy(scrollToTop = false) }
    }

    private fun ensureEpoch(
        epoch: Int,
        leafRef: String,
        leafName: String,
        leafTimeSec: Long?,
        nowSec: Int
    ) {
        val seen = seenEpoch
        seenEpoch = epoch
        if (seen == null) {
            seedIfAbsent(leafRef, leafName, leafTimeSec, nowSec)
            return
        }
        if (seen == epoch) {
            return
        }
        loadJob?.cancel()
        loadJob = null
        persist {
            it.copy(
                bouquetRef = leafRef,
                bouquetName = leafName,
                timeSec = (leafTimeSec ?: nowSec.toLong())
            )
        }
        _uiState.update {
            it.copy(
                events = emptyList(),
                emptyMessage = null,
                refreshing = false,
                scrollToTop = true
            )
        }
    }

    private fun applyLeaf(leafRef: String, leafName: String) {
        val state = _uiState.value
        val resolvedRef = EpgBouquetRestore.resolveRef(leafRef, state.bouquetRef)
        if (resolvedRef == state.bouquetRef) {
            return
        }
        val resolvedName =
            EpgBouquetRestore.resolveName(leafName, state.bouquetName, state.bouquetRef)
        persist { it.copy(bouquetRef = resolvedRef, bouquetName = resolvedName) }
        _uiState.update { it.copy(scrollToTop = true) }
    }

    private fun seedIfAbsent(leafRef: String, leafName: String, leafTimeSec: Long?, nowSec: Int) {
        val refAbsent = EpgBouquetNavSavedKeys.BOUQUET_REF !in savedStateHandle
        val timeAbsent = EpgBouquetNavSavedKeys.TIME_SEC !in savedStateHandle
        if (!refAbsent && !timeAbsent) {
            return
        }
        persist { saved ->
            saved.copy(
                bouquetRef = if (refAbsent) leafRef else saved.bouquetRef,
                bouquetName = if (refAbsent) leafName else saved.bouquetName,
                timeSec = if (timeAbsent) leafTimeSec ?: nowSec.toLong() else saved.timeSec
            )
        }
    }

    private fun apply(load: EventListLoad) {
        _uiState.update {
            when (load) {
                is EventListLoad.Events -> it.copy(
                    refreshing = false,
                    events = load.events,
                    emptyMessage = if (load.events.isEmpty()) {
                        UiText.Resource(R.string.no_list_item)
                    } else {
                        null
                    }
                )

                is EventListLoad.Failed -> it.copy(
                    refreshing = false,
                    events = emptyList(),
                    emptyMessage = load.error.contentErrorText()
                )
            }
        }
    }

    private fun persist(change: (EpgBouquetNavSaved) -> EpgBouquetNavSaved) {
        val next = change(readEpgBouquetNavSaved(savedStateHandle))
        next.writeTo(savedStateHandle)
        _uiState.update {
            it.copy(
                bouquetRef = next.bouquetRef,
                bouquetName = next.bouquetName,
                timeSec = next.timeSec?.toInt(),
                waitingForPicker = next.waitingForPicker
            )
        }
    }
}
