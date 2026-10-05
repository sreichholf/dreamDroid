package net.reichholf.dreamdroid.ui.pick

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
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.text.UiText

/** The bouquet picker. [emptyMessage] is shown instead of the list when [items] is empty. */
data class PickServiceUiState(
    val items: List<Service> = emptyList(),
    val refreshing: Boolean = false,
    val emptyMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(if (refreshing) R.string.loading else R.string.services)
}

/**
 * TV then radio bouquets for [PickServiceDestination]. The receiver's lists, or the Room tab
 * strips when it fails. Loads when created.
 */
@HiltViewModel
class PickServiceViewModel @Inject constructor(private val services: ServiceRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(PickServiceUiState())
    val uiState: StateFlow<PickServiceUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        reload()
    }

    /** Loads the bouquets. Room paints first unless [forceRefresh]. */
    fun reload(forceRefresh: Boolean = false) {
        _uiState.update {
            it.copy(
                refreshing = true,
                emptyMessage = if (it.items.isEmpty()) UiText.Resource(R.string.loading) else null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            services.bouquets(forceRefresh).collect { load -> apply(load) }
        }
    }

    private fun apply(load: BouquetListLoad) {
        _uiState.value = when (load) {
            is BouquetListLoad.Loaded -> {
                val rows = load.bouquets.tv + load.bouquets.radio
                PickServiceUiState(
                    items = rows,
                    emptyMessage = if (rows.isEmpty()) {
                        UiText.Resource(R.string.no_list_item)
                    } else {
                        null
                    }
                )
            }

            is BouquetListLoad.Failed ->
                PickServiceUiState(emptyMessage = load.error.contentErrorText())
        }
    }
}
