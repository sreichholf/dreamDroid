package net.reichholf.dreamdroid.ui.device

import net.reichholf.dreamdroid.enigma.DeviceInfo

object DeviceInfoSavedKeys {
    const val INFO = "device_info"
    const val READY = "device_info_ready"
}

data class DeviceInfoSaved(val info: DeviceInfo? = null, val ready: Boolean = false)

interface DeviceInfoSavedAccess {
    fun getInfo(): DeviceInfo?
    fun setInfo(info: DeviceInfo?)
    fun getReady(): Boolean
    fun setReady(ready: Boolean)
}

class MapDeviceInfoSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    DeviceInfoSavedAccess {
    override fun getInfo(): DeviceInfo? = values[DeviceInfoSavedKeys.INFO] as DeviceInfo?

    override fun setInfo(info: DeviceInfo?) {
        if (info == null) {
            values.remove(DeviceInfoSavedKeys.INFO)
        } else {
            values[DeviceInfoSavedKeys.INFO] = info
        }
    }

    override fun getReady(): Boolean = values[DeviceInfoSavedKeys.READY] as? Boolean ?: false

    override fun setReady(ready: Boolean) {
        values[DeviceInfoSavedKeys.READY] = ready
    }
}

fun readDeviceInfoSaved(access: DeviceInfoSavedAccess): DeviceInfoSaved = DeviceInfoSaved(
    info = access.getInfo(),
    ready = access.getReady()
)

fun DeviceInfoSaved.writeTo(access: DeviceInfoSavedAccess) {
    access.setInfo(info)
    access.setReady(ready)
}

fun shouldLoadDeviceInfo(info: DeviceInfo?): Boolean = info == null || info.isEmpty()
