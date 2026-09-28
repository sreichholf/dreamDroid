package net.reichholf.dreamdroid.ui.pick

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
import net.reichholf.dreamdroid.data.BouquetListLoad
import net.reichholf.dreamdroid.data.ServiceListLoad
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.zap.ZapListMapper

/**
 * The timer service picker: the bouquet list while [bouquetRef] is empty, else the channels
 * of that bouquet. [emptyMessage] is shown instead of the list when [items] is empty.
 */
data class TimerServicePickUiState(
    val bouquetRef: String = "",
    val bouquetName: String = "",
    val items: List<Service> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null
) {
    val showsBouquets: Boolean
        get() = bouquetRef.isEmpty()

    val title: UiText
        get() = when {
            refreshing -> UiText.Resource(R.string.loading)
            showsBouquets || bouquetName.isEmpty() -> UiText.Resource(R.string.service)
            else -> UiText.Raw(bouquetName)
        }
}

/**
 * Bouquet then channel for the phone timer editor and the TV timer overlay. The open bouquet
 * survives process death. The receiver's lists, or Room's tab strips and rosters when it
 * fails. Loads when created.
 */
@HiltViewModel
class TimerServicePickViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val services: ServiceRepository
) : ViewModel() {
    private val _uiState: MutableStateFlow<TimerServicePickUiState>
    val uiState: StateFlow<TimerServicePickUiState>

    private var bouquets: List<Service> = emptyList()
    private var loadJob: Job? = null

    init {
        val saved = readTimerServicePickSaved(savedStateHandle)
        _uiState = MutableStateFlow(
            TimerServicePickUiState(bouquetRef = saved.bouquetRef, bouquetName = saved.bouquetName)
        )
        uiState = _uiState.asStateFlow()
        reload()
    }

    fun reload() {
        if (_uiState.value.showsBouquets) {
            loadBouquets()
        } else {
            loadServices()
        }
    }

    /** The picker is shown again; a list that never loaded loads again. */
    fun onShown() {
        val state = _uiState.value
        if (state.items.isEmpty() && !state.refreshing) {
            reload()
        }
    }

    /** Back from a bouquet's channels to the bouquet list. */
    fun showBouquetList() {
        loadJob?.cancel()
        loadJob = null
        open(TimerServicePickSaved())
        if (bouquets.isEmpty()) {
            _uiState.update { it.copy(items = emptyList()) }
            loadBouquets()
        } else {
            _uiState.update { it.copy(items = bouquets, refreshing = false, emptyMessage = null) }
        }
    }

    /**
     * A bouquet row opens that bouquet and returns null; a channel row is the pick. Markers
     * are ignored.
     */
    fun onRowClick(service: Service): Service? {
        if (ServiceKeys.isMarker(service.reference)) {
            return null
        }
        if (!_uiState.value.showsBouquets) {
            return service
        }
        open(TimerServicePickSaved(bouquetRef = service.reference, bouquetName = service.name))
        _uiState.update { it.copy(items = emptyList()) }
        loadServices()
        return null
    }

    private fun loadBouquets() {
        startLoading()
        loadJob = viewModelScope.launch {
            val load = services.bouquets()
            when (load) {
                is BouquetListLoad.Loaded -> {
                    bouquets = load.bouquets.tv + load.bouquets.radio
                    show(bouquets)
                }

                is BouquetListLoad.Failed -> fail(load.error.contentErrorText())
            }
        }
    }

    private fun loadServices() {
        startLoading()
        val ref = _uiState.value.bouquetRef
        loadJob = viewModelScope.launch {
            when (val load = services.services(ref)) {
                is ServiceListLoad.Services -> show(ZapListMapper.rowsFrom(load.services))
                is ServiceListLoad.Failed -> fail(load.error.contentErrorText())
            }
        }
    }

    private fun startLoading() {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.items.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
    }

    private fun show(rows: List<Service>) {
        _uiState.update {
            it.copy(
                items = rows,
                refreshing = false,
                emptyMessage = if (rows.isEmpty()) UiText.Resource(R.string.no_list_item) else null
            )
        }
    }

    private fun fail(message: UiText) {
        _uiState.update { it.copy(items = emptyList(), refreshing = false, emptyMessage = message) }
    }

    private fun open(next: TimerServicePickSaved) {
        next.writeTo(savedStateHandle)
        _uiState.update { it.copy(bouquetRef = next.bouquetRef, bouquetName = next.bouquetName) }
    }
}
