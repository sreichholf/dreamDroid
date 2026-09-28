package net.reichholf.dreamdroid.ui.device

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
import net.reichholf.dreamdroid.data.ReceiverRepository
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.ui.text.UiText

/**
 * Device info of the active receiver. [DeviceInfoUiState.loading] is true until the first
 * load finishes; a failed refresh keeps the info already shown.
 */
data class DeviceInfoUiState(
    val info: DeviceInfo? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val userMessage: UiText? = null
) {
    val title: UiText
        get() = UiText.Resource(if (refreshing) R.string.loading else R.string.device_info)
}

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val receiver: ReceiverRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        val saved = savedStateHandle.get<DeviceInfo>(KEY_INFO)
        if (saved != null && !saved.isEmpty()) {
            _uiState.value = DeviceInfoUiState(info = saved, loading = false)
        } else {
            refresh()
        }
    }

    fun refresh() {
        loadJob?.cancel()
        _uiState.update { it.copy(loading = it.info == null, refreshing = true) }
        loadJob = viewModelScope.launch {
            val response = receiver.deviceInfo()
            val info = response.value?.takeUnless { it.isEmpty() }
            if (info == null) {
                val message = response.error?.contentErrorText()
                    ?: UiText.Resource(R.string.error_parsing)
                _uiState.update {
                    it.copy(loading = false, refreshing = false, userMessage = message)
                }
                return@launch
            }
            savedStateHandle[KEY_INFO] = info
            _uiState.update { it.copy(info = info, loading = false, refreshing = false) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private companion object {
        const val KEY_INFO = "device_info"
    }
}
