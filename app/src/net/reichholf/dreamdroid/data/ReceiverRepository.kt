package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.PowerCommand
import net.reichholf.dreamdroid.enigma.PowerState
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.Volume
import net.reichholf.dreamdroid.enigma.VolumeCommand
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.video.ZapAndStream

/**
 * Whether a live service of the active profile can be streamed, from
 * [ReceiverRepository.liveStream].
 */
sealed interface LiveStream {
    /** Play [url], the stream of the service [reference]. */
    data class Ready(val reference: String, val url: String) : LiveStream

    /** The receiver cannot stream the service; show [message]. */
    data class Failed(val message: UiText) : LiveStream
}

/**
 * Receiver state and commands of the active profile. Without one every call answers
 * nothing and asks no receiver.
 */
@Singleton
class ReceiverRepository @Inject constructor(
    private val clients: ReceiverApiFactory,
    private val profiles: ProfileRepository
) {
    suspend fun deviceInfo(): EnigmaResponse<DeviceInfo> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).deviceInfo()
    }

    suspend fun signal(): EnigmaResponse<Signal> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).signal()
    }

    /** A JPEG of video and OSD at the receiver's resolution. */
    suspend fun screenshot(): EnigmaResponse<ByteArray> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).screenshot()
    }

    /** Zaps the receiver to [reference], a service or a recording. */
    suspend fun zap(reference: String): EnigmaResponse<SimpleResult> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).zap(reference)
    }

    /**
     * The stream of the live service [reference]. A zap-and-stream profile ([ZapAndStream])
     * zaps first; a failed zap streams nothing, as does a missing profile.
     */
    suspend fun liveStream(reference: String): LiveStream {
        val profile = profiles.current.value
            ?: return LiveStream.Failed(UiText.Resource(R.string.no_profile_body))
        if (ZapAndStream.required(profile)) {
            if (reference.isEmpty()) {
                return LiveStream.Failed(UiText.Resource(R.string.get_content_error))
            }
            val response = zap(reference)
            if (response.value == null || response.error != null) {
                return LiveStream.Failed(response.userMessageText())
            }
        }
        return LiveStream.Ready(reference, clients.forProfile(profile).liveStreamUrl(reference))
    }

    /** The service the receiver is tuned to, with its now and next event. */
    suspend fun currentService(): EnigmaResponse<CurrentService> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).currentService()
    }

    /**
     * Returns once the startup profile check stored the active profile's device info, or
     * after [timeoutMs], or at once when no profile is active: no check can arrive for one.
     * A caller with nothing to paint waits here, so its first request does not race the check.
     */
    suspend fun awaitProfileCheck(timeoutMs: Long = PROFILE_CHECK_WAIT_MS) {
        withTimeoutOrNull(timeoutMs) {
            var profile = profiles.current.value
            while (profile != null && profiles.deviceInfo(profile) == null) {
                delay(PROFILE_CHECK_POLL_MS)
                profile = profiles.current.value
            }
        }
    }

    private companion object {
        const val PROFILE_CHECK_WAIT_MS = 20_000L
        const val PROFILE_CHECK_POLL_MS = 100L
    }

    /** Plays [reference], a media player service ref, on the receiver of [profile]. */
    suspend fun playMedia(profile: Profile, reference: String): EnigmaResponse<SimpleResult> =
        clients.forProfile(profile).playMedia(reference)

    /** Runs the volume [command]; the answer carries the new level. */
    suspend fun setVolume(command: VolumeCommand): EnigmaResponse<Volume> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).setVolume(command)
    }

    /** Runs the power [command]; the answer carries the new state. */
    suspend fun setPowerState(command: PowerCommand): EnigmaResponse<PowerState> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).setPowerState(command)
    }

    /** Reads the sleep timer. */
    suspend fun sleepTimer(): EnigmaResponse<SleepTimer> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).sleepTimer()
    }

    /** Writes the sleep timer; the answer carries the stored timer. */
    suspend fun setSleepTimer(
        time: String?,
        action: String?,
        enabled: Boolean
    ): EnigmaResponse<SleepTimer> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).setSleepTimer(time, action, enabled)
    }

    /** Shows a message on the receiver. */
    suspend fun sendMessage(
        text: String?,
        type: String?,
        timeout: String?
    ): EnigmaResponse<SimpleResult> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).sendMessage(text, type, timeout)
    }

    /** Sends a remote-control key press. */
    suspend fun remoteCommand(
        keyCode: Int,
        simpleRemote: Boolean,
        longClick: Boolean
    ): EnigmaResponse<SimpleResult> {
        val profile = profiles.current.value ?: return EnigmaResponse(null)
        return clients.forProfile(profile).remoteCommand(keyCode, simpleRemote, longClick)
    }
}
