package net.reichholf.dreamdroid.enigma

import java.io.Serializable

data class DeviceFrontend(
    val name: String = "",
    val model: String = "",
) : Serializable

data class DeviceNic(
    val name: String = "",
    val mac: String = "",
    val dhcp: String = "",
    val ip: String = "",
    val gateway: String = "",
    val netmask: String = "",
) : Serializable

data class DeviceHdd(
    val model: String = "",
    val capacity: String = "",
    val free: String = "",
) : Serializable

/**
 * Typed `/web/deviceinfo` payload.
 */
data class DeviceInfo(
    val guiVersion: String = "",
    val imageVersion: String = "",
    val interfaceVersion: String = "",
    val frontProcessorVersion: String = "",
    val deviceName: String = "",
    val frontends: List<DeviceFrontend> = emptyList(),
    val nics: List<DeviceNic> = emptyList(),
    val hdds: List<DeviceHdd> = emptyList(),
) : Serializable {
    fun isEmpty(): Boolean {
        return guiVersion.isEmpty()
            && imageVersion.isEmpty()
            && interfaceVersion.isEmpty()
            && frontProcessorVersion.isEmpty()
            && deviceName.isEmpty()
            && frontends.isEmpty()
            && nics.isEmpty()
            && hdds.isEmpty()
    }
}
