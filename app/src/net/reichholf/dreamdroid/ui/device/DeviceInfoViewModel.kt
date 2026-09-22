package net.reichholf.dreamdroid.ui.device

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.loadDeviceInfo

class DeviceInfoViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    AndroidViewModel(application) {
    val uiState: DeviceInfoUiState = DeviceInfoUiState()

    var refreshing by mutableStateOf(false)
        private set

    var toolbarTitle by mutableStateOf("")
        private set

    var errorText by mutableStateOf<String?>(null)
        private set

    private val savedAccess = HandleDeviceInfoSavedAccess(savedStateHandle)
    private var saved = DeviceInfoSaved()
    private var started = false
    private var loadJob: Job? = null

    private val hddCapacityFormat: (capacity: String, free: String) -> String = { capacity, free ->
        val app = getApplication<Application>()
        String.format(app.getString(R.string.hdd_capacity), capacity, free)
    }

    init {
        saved = readDeviceInfoSaved(savedAccess)
        restoreDeviceInfoUiState(uiState, saved.info, saved.ready, hddCapacityFormat)
        toolbarTitle = getApplication<Application>().getString(R.string.device_info)
    }

    fun start() {
        if (started) {
            return
        }
        started = true
        if (shouldLoadDeviceInfo(saved.info)) {
            reload()
            return
        }
        saved = DeviceInfoSaved(info = saved.info, ready = true)
        saved.writeTo(savedAccess)
        uiState.apply(saved.info, hddCapacityFormat)
        toolbarTitle = getApplication<Application>().getString(R.string.device_info)
    }

    fun reload() {
        val app = getApplication<Application>()
        if (!saved.ready) {
            uiState.beginLoading()
        }
        refreshing = true
        toolbarTitle = app.getString(R.string.loading)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = loadDeviceInfo(app)
            refreshing = false
            toolbarTitle = app.getString(R.string.device_info)
            if (!result.success || result.info == null) {
                if (!saved.ready) {
                    uiState.apply(null, hddCapacityFormat)
                }
                errorText = result.errorText?.takeIf { it.isNotEmpty() }
                    ?: app.getString(R.string.not_available)
                return@launch
            }
            saved = DeviceInfoSaved(info = result.info, ready = true)
            saved.writeTo(savedAccess)
            uiState.apply(result.info, hddCapacityFormat)
        }
    }

    fun consumeError() {
        errorText = null
    }
}

private class HandleDeviceInfoSavedAccess(private val handle: SavedStateHandle) :
    DeviceInfoSavedAccess {
    override fun getInfo(): DeviceInfo? = handle.get<DeviceInfo>(DeviceInfoSavedKeys.INFO)

    override fun setInfo(info: DeviceInfo?) {
        if (info == null) {
            handle.remove<DeviceInfo>(DeviceInfoSavedKeys.INFO)
        } else {
            handle[DeviceInfoSavedKeys.INFO] = info
        }
    }

    override fun getReady(): Boolean = handle.get<Boolean>(DeviceInfoSavedKeys.READY) ?: false

    override fun setReady(ready: Boolean) {
        handle[DeviceInfoSavedKeys.READY] = ready
    }
}
