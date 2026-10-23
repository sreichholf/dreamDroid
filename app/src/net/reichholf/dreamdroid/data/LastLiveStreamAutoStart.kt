package net.reichholf.dreamdroid.data

import javax.inject.Inject
import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

/** What starting the last live channel on launch came to. */
sealed interface LastStreamStart {
    /** Play [stream], the saved [service]. */
    data class Play(val stream: LiveStream.Ready, val service: LastLiveStream) : LastStreamStart

    /** The receiver could not stream the saved service. */
    data class Failed(val message: UiText) : LastStreamStart
}

/** Decides whether the app starts the last live channel when it launches. */
class LastLiveStreamAutoStart @Inject constructor(
    private val settings: SettingsRepository,
    private val lastStreams: LastLiveStreamRepository,
    private val receiver: ReceiverRepository,
    private val sessions: SessionConnectionHolder
) {
    /**
     * Asks the receiver for the saved channel's stream. Call it once a profile check applied
     * its result to the session: it does not wait, so a later check cannot answer for this
     * one. Null when the setting is off, the integrated player is off (only it records the
     * last channel), that check did not find the receiver online, or nothing was saved for
     * the active profile.
     */
    suspend fun streamToStart(): LastStreamStart? {
        val current = settings.current()
        if (!current.autoStartLastStream ||
            !current.integratedVideoPlayer ||
            sessions.status.value.session != ConnectionStatus.Session.Online
        ) {
            return null
        }
        val service = lastStreams.forActiveProfile() ?: return null
        return when (val stream = receiver.liveStream(service.reference)) {
            is LiveStream.Ready -> LastStreamStart.Play(stream, service)
            is LiveStream.Failed -> LastStreamStart.Failed(stream.message)
        }
    }
}
