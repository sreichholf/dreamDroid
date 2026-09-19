package net.reichholf.dreamdroid.tv.ui

import net.reichholf.dreamdroid.ui.session.ConnectionStatus
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckCheckingUi
import net.reichholf.dreamdroid.ui.session.shouldShowProfileCheckFailedUi

/**
 * TV session helpers. Online / Offline / Checking stay on
 * [net.reichholf.dreamdroid.ui.session.SessionConnectionHolder] — this file
 * only maps that status onto TV chrome, the ProfileCheck gate, and streaming.
 *
 * Streaming (VideoActivity, overlay zap, hub stream, MultiEPG stream) is
 * available only while [ConnectionStatus.Session.Online].
 */
fun ConnectionStatus.allowsStreaming(): Boolean = session == ConnectionStatus.Session.Online

/**
 * Room can paint: skip Enigma HTTP unless the session is already Online.
 * Checking + cache must not wait on a dead box before first paint.
 */
fun shouldSkipTvHubHttp(status: ConnectionStatus, hasCache: Boolean): Boolean =
    hasCache && status.session != ConnectionStatus.Session.Online

/**
 * TV [net.reichholf.dreamdroid.activities.VideoActivity] may stay up only
 * while Online, or after playback already started (a later Offline probe must
 * not tear down the current stream). Phone is unchanged.
 */
fun shouldKeepTvStreamingActivity(
    isTelevision: Boolean,
    status: ConnectionStatus,
    playbackAlreadyStarted: Boolean
): Boolean {
    if (!isTelevision) {
        return true
    }
    if (status.allowsStreaming()) {
        return true
    }
    return playbackAlreadyStarted
}

fun shouldShowTvSessionRecheck(status: ConnectionStatus): Boolean = !status.checking &&
    (
        status.session == ConnectionStatus.Session.Offline ||
            (status.session == null && status.lastFailure != null)
        )

/**
 * Raw browse [errorText] stays off Settings (the session chip covers that
 * header). Content headers show it only when this view has nothing painted.
 */
fun shouldShowTvBrowseError(
    selectedHeaderId: String,
    loading: Boolean,
    errorText: String?,
    hasPaintedContent: Boolean
): Boolean = selectedHeaderId != TvComposeHubHost.HEADER_SETTINGS_ID &&
    !loading &&
    errorText != null &&
    !hasPaintedContent

sealed class TvSessionGate {
    data object None : TvSessionGate()

    data class Checking(val message: String) : TvSessionGate()

    data class Failed(val title: String, val message: String) : TvSessionGate()
}

fun tvSessionGate(
    status: ConnectionStatus,
    hasCache: Boolean,
    checkingMessage: String,
    failedTitle: String,
    failedMessage: String
): TvSessionGate {
    if (status.checking && shouldShowProfileCheckCheckingUi(hasCache)) {
        return TvSessionGate.Checking(checkingMessage)
    }
    if (status.session == null &&
        status.lastFailure != null &&
        shouldShowProfileCheckFailedUi(hasCache, status.lastFailure)
    ) {
        return TvSessionGate.Failed(failedTitle, failedMessage)
    }
    return TvSessionGate.None
}

/**
 * Movie drawer headers. Never advertise the `/hdd/movie` HTTP fallback as a
 * live location — only receiver-backed lists or a Room snapshot.
 */
fun movieHeadersForTvHub(
    locationsFromReceiver: Boolean,
    liveLocations: List<String>,
    cachedLocations: List<String>?
): List<String> {
    val live = liveLocations.filter { it.isNotBlank() }
    if (locationsFromReceiver) {
        return live
    }
    return cachedLocations?.filter { it.isNotBlank() }.orEmpty()
}
