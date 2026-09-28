package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.helpers.NameValuePair

/** Receiver state and commands of the active profile. */
@Singleton
class ReceiverRepository @Inject constructor(private val clients: EnigmaClientFactory) {
    suspend fun deviceInfo(): EnigmaResponse<DeviceInfo> = clients.current().getDeviceInfo()

    suspend fun signal(): EnigmaResponse<Signal> = clients.current().getSignal()

    /**
     * A JPEG of video and OSD at the receiver's resolution. The receiver writes the grab to
     * a timestamped file under `/tmp` first.
     */
    suspend fun screenshot(): EnigmaResponse<ByteArray> = clients.current().getScreenshot(
        listOf(
            NameValuePair("format", "jpg"),
            NameValuePair("filename", "/tmp/dreamDroid-${System.currentTimeMillis() / 1000}")
        )
    )
}
