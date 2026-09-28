package net.reichholf.dreamdroid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.enigma2.DeviceDetector

/**
 * Receivers announced on the local network, as unsaved profiles. An interface because
 * tests cannot answer mDNS; the app binds [MdnsReceiverDiscovery].
 */
fun interface ReceiverDiscovery {
    suspend fun find(): List<Profile>
}

object MdnsReceiverDiscovery : ReceiverDiscovery {
    override suspend fun find(): List<Profile> =
        withContext(Dispatchers.IO) { DeviceDetector.getAvailableHosts() }
}
