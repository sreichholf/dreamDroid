package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.helpers.NameValuePair

/** Receiver state and commands of the active profile. */
@Singleton
class ReceiverRepository @Inject constructor(
    private val clients: EnigmaClientFactory,
    private val profiles: ProfileRepository
) {
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

    /** Zaps the receiver to [reference], a service or a recording. */
    suspend fun zap(reference: String): EnigmaResponse<SimpleResult> =
        clients.current().zap(listOf(NameValuePair("sRef", reference)))

    /** The service the receiver is tuned to, with its now and next event. */
    suspend fun currentService(): EnigmaResponse<CurrentService> = clients.current().getCurrent()

    /**
     * Returns once the startup profile check stored the active profile's device info, or
     * after [timeoutMs]. A caller with nothing to paint waits here, so its first request
     * does not race the check.
     */
    suspend fun awaitProfileCheck(timeoutMs: Long = PROFILE_CHECK_WAIT_MS) {
        withTimeoutOrNull(timeoutMs) {
            while (profiles.deviceInfo(profiles.requireCurrent()) == null) {
                delay(PROFILE_CHECK_POLL_MS)
            }
        }
    }

    private companion object {
        const val PROFILE_CHECK_WAIT_MS = 20_000L
        const val PROFILE_CHECK_POLL_MS = 100L
    }
}
