package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse

/** Receiver state and commands of the active profile. */
@Singleton
class ReceiverRepository @Inject constructor(private val clients: EnigmaClientFactory) {
    suspend fun deviceInfo(): EnigmaResponse<DeviceInfo> = clients.current().getDeviceInfo()
}
