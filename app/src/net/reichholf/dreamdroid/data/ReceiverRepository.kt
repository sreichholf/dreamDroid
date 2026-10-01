package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.CurrentService
import net.reichholf.dreamdroid.enigma.DeviceInfo
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.PowerState
import net.reichholf.dreamdroid.enigma.Signal
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.enigma.SleepTimer
import net.reichholf.dreamdroid.enigma.Volume
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import net.reichholf.dreamdroid.helpers.enigma2.Message
import net.reichholf.dreamdroid.helpers.enigma2.PowerState as PowerStateKeys
import net.reichholf.dreamdroid.helpers.enigma2.Remote
import net.reichholf.dreamdroid.helpers.enigma2.SleepTimer as SleepTimerKeys
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

/** Receiver state and commands of the active profile. */
@Singleton
class ReceiverRepository @Inject constructor(
    private val clients: EnigmaClientFactory,
    private val profiles: ProfileRepository
) {
    suspend fun deviceInfo(): EnigmaResponse<DeviceInfo> = clients.current().getDeviceInfo()

    suspend fun signal(): EnigmaResponse<Signal> = clients.current().getSignal()

    /**
     * A JPEG of video and OSD at the receiver's resolution, from `/grab` (which writes it to a
     * timestamped file under `/tmp` first), or from `/screenshot` when `/grab` returns no image.
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

    /**
     * The stream of the live service [reference]. A zap-and-stream profile ([ZapAndStream])
     * zaps first; a failed zap streams nothing.
     */
    suspend fun liveStream(reference: String): LiveStream {
        val profile = profiles.requireCurrent()
        if (ZapAndStream.required(profile)) {
            if (reference.isEmpty()) {
                return LiveStream.Failed(UiText.Resource(R.string.get_content_error))
            }
            val response = zap(reference)
            if (response.value == null || response.error != null) {
                return LiveStream.Failed(response.userMessageText())
            }
        }
        return LiveStream.Ready(reference, EnigmaUrls.stream(profile, reference))
    }

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

    /** Plays [reference], a media player service ref, on the receiver of [profile]. */
    suspend fun playMedia(profile: Profile, reference: String): EnigmaResponse<SimpleResult> =
        clients.forProfile(profile).playMedia(listOf(NameValuePair("file", reference)))

    /** Runs the volume [command] (`up`, `down`, `mute`); the answer carries the new level. */
    suspend fun setVolume(command: String): EnigmaResponse<Volume> =
        clients.current().setVolume(listOf(NameValuePair("set", command)))

    /** Sets the power [state]; the answer carries the new state. */
    suspend fun setPowerState(state: String): EnigmaResponse<PowerState> =
        clients.current().setPowerState(PowerStateKeys.getStateParams(state))

    /** Reads the sleep timer. */
    suspend fun sleepTimer(): EnigmaResponse<SleepTimer> = clients.current().sleepTimer(emptyList())

    /** Writes the sleep timer; the answer carries the stored timer. */
    suspend fun setSleepTimer(
        time: String?,
        action: String?,
        enabled: Boolean
    ): EnigmaResponse<SleepTimer> = clients.current().sleepTimer(
        listOf(
            NameValuePair("cmd", SleepTimerKeys.CMD_SET),
            NameValuePair("time", time),
            NameValuePair("action", action),
            NameValuePair("enabled", if (enabled) Python.TRUE else Python.FALSE)
        )
    )

    /** Shows a message on the receiver. */
    suspend fun sendMessage(
        text: String?,
        type: String?,
        timeout: String?
    ): EnigmaResponse<SimpleResult> =
        clients.current().sendMessage(Message.getParams(text, type, timeout))

    /** Sends a remote-control key press. */
    suspend fun remoteCommand(
        keyCode: Int,
        simpleRemote: Boolean,
        longClick: Boolean
    ): EnigmaResponse<SimpleResult> {
        val params = ArrayList<NameValuePair>().apply {
            add(NameValuePair("command", keyCode.toString()))
            add(NameValuePair("rcu", if (simpleRemote) "standard" else "advanced"))
            if (longClick) {
                add(NameValuePair("type", Remote.CLICK_TYPE_LONG))
            }
        }
        return clients.current().remoteCommand(params)
    }
}
