package net.reichholf.dreamdroid.ui.device

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.helpers.setOrRemove

object DeviceInfoSavedKeys {
    const val INFO = "device_info"
    const val READY = "device_info_ready"
}

data class DeviceInfoSaved(val info: DeviceInfo? = null, val ready: Boolean = false)

fun readDeviceInfoSaved(handle: SavedStateHandle): DeviceInfoSaved = DeviceInfoSaved(
    info = handle.get<DeviceInfo>(DeviceInfoSavedKeys.INFO),
    ready = handle.get<Boolean>(DeviceInfoSavedKeys.READY) ?: false
)

fun DeviceInfoSaved.writeTo(handle: SavedStateHandle) {
    handle.setOrRemove(DeviceInfoSavedKeys.INFO, info)
    handle[DeviceInfoSavedKeys.READY] = ready
}

fun shouldLoadDeviceInfo(info: DeviceInfo?): Boolean = info == null || info.isEmpty()
