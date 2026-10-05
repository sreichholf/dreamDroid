package net.reichholf.dreamdroid.ui.autotimer

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
import net.reichholf.dreamdroid.enigma.autotimer.Target
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.enigma2.Service as ServiceKeys
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.ui.zap.ZapListMapper

/**
 * The bouquet list while [bouquet] is null, else that bouquet's channels. [selected] are the
 * bouquets and channels picked so far, in the order they were picked.
 */
data class AutoTimerTargetPickUiState(
    val bouquet: Target.Bouquet? = null,
    val rows: List<Service> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null,
    val selected: List<Target> = emptyList()
) {
    val title: UiText
        get() = bouquet?.let { UiText.Raw(it.name) } ?: UiText.Resource(R.string.autotimer_targets)

    /** The target a row stands for: a whole bouquet in the bouquet list, else a channel. */
    fun targetOf(row: Service): Target = if (bouquet == null) {
        Target.Bouquet(row.reference, row.name)
    } else {
        Target.Channel(row.reference, row.name)
    }

    fun isSelected(row: Service): Boolean = selected.any { it.ref == row.reference }
}

/**
 * Picks bouquets and channels for an AutoTimer. A bouquet is picked whole with its checkbox or
 * opened to pick single channels. The open bouquet and the picks survive process death.
 */
@HiltViewModel
class AutoTimerTargetPickViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val services: ServiceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AutoTimerTargetPickUiState(
            bouquet = savedStateHandle.get<Target.Bouquet>(KEY_BOUQUET),
            selected = savedStateHandle.get<ArrayList<Target>>(KEY_SELECTED).orEmpty()
        )
    )
    val uiState: StateFlow<AutoTimerTargetPickUiState> = _uiState.asStateFlow()

    private var bouquets: List<Service> = emptyList()

    /** Whether [bouquets] came from the receiver; Room's tab strips are asked again. */
    private var bouquetsLive = false
    private var loadJob: Job? = null

    init {
        reload()
    }

    /** Loads the list on screen. Room paints first unless [forceRefresh]. */
    fun reload(forceRefresh: Boolean = false) {
        if (_uiState.value.bouquet == null) {
            loadBouquets(forceRefresh)
        } else {
            loadChannels(forceRefresh)
        }
    }

    fun open(bouquet: Service) {
        if (_uiState.value.bouquet != null) {
            return
        }
        setBouquet(Target.Bouquet(bouquet.reference, bouquet.name))
        _uiState.update { it.copy(rows = emptyList()) }
        loadChannels()
    }

    /** Back from a bouquet's channels to the bouquet list. */
    fun showBouquets() {
        loadJob?.cancel()
        setBouquet(null)
        if (!bouquetsLive) {
            _uiState.update { it.copy(rows = emptyList()) }
            loadBouquets()
        } else {
            _uiState.update { it.copy(rows = bouquets, refreshing = false, emptyMessage = null) }
        }
    }

    fun toggle(row: Service) {
        if (ServiceKeys.isMarker(row.reference)) {
            return
        }
        val state = _uiState.value
        val selected = if (state.isSelected(row)) {
            state.selected.filterNot { it.ref == row.reference }
        } else {
            state.selected + state.targetOf(row)
        }
        savedStateHandle[KEY_SELECTED] = ArrayList(selected)
        _uiState.update { it.copy(selected = selected) }
    }

    private fun setBouquet(bouquet: Target.Bouquet?) {
        savedStateHandle[KEY_BOUQUET] = bouquet
        _uiState.update { it.copy(bouquet = bouquet) }
    }

    private fun loadBouquets(forceRefresh: Boolean = false) {
        startLoading()
        loadJob = viewModelScope.launch {
            services.bouquets(forceRefresh).collect { load ->
                when (load) {
                    is BouquetListLoad.Loaded -> {
                        bouquets = load.bouquets.tv + load.bouquets.radio
                        bouquetsLive = !load.cached
                        show(bouquets)
                    }

                    is BouquetListLoad.Failed -> fail(load.error.contentErrorText())
                }
            }
        }
    }

    private fun loadChannels(forceRefresh: Boolean = false) {
        val ref = _uiState.value.bouquet?.ref ?: return
        startLoading()
        loadJob = viewModelScope.launch {
            services.services(ref, forceRefresh).collect { load ->
                when (load) {
                    // Without markers; a channel the bouquet lists twice is one target.
                    is ServiceListLoad.Services -> show(
                        ZapListMapper.rowsFrom(load.services).distinctBy { it.reference }
                    )

                    is ServiceListLoad.Failed -> fail(load.error.contentErrorText())
                }
            }
        }
    }

    private fun startLoading() {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.rows.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
    }

    private fun show(rows: List<Service>) {
        _uiState.update {
            it.copy(
                rows = rows,
                refreshing = false,
                emptyMessage = if (rows.isEmpty()) UiText.Resource(R.string.no_list_item) else null
            )
        }
    }

    private fun fail(message: UiText) {
        _uiState.update { it.copy(rows = emptyList(), refreshing = false, emptyMessage = message) }
    }

    private companion object {
        const val KEY_BOUQUET = "autotimer_target_pick_bouquet"
        const val KEY_SELECTED = "autotimer_target_pick_selected"
    }
}
