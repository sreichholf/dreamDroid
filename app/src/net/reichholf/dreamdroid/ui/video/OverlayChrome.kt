package net.reichholf.dreamdroid.ui.video

import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.helpers.DateTime
import net.reichholf.dreamdroid.video.PlayerState

/** One line of the now/next block: start time (a recording's position), title, duration. */
data class OverlayRow(val start: String, val title: String, val duration: String)

/** The progress bar, in seconds. */
data class OverlayProgress(val position: Int, val max: Int)

/**
 * The overlay's title, buttons, now/next rows and progress bar for what plays. A null [now],
 * [next] or [progress] hides that row or disables the bar. [nowEvent] is the live event
 * behind [now].
 */
data class OverlayChrome(
    val title: String,
    val showInfoButton: Boolean,
    val showPvrControls: Boolean,
    val seekable: Boolean,
    val now: OverlayRow?,
    val nowEvent: Event?,
    val next: OverlayRow?,
    val progress: OverlayProgress?
)

/**
 * Seekbar length for a seekable stream whose engine reports no length; the bar then maps
 * [PlayerState.position] onto it.
 */
const val FAKE_PROGRESS_LENGTH_SEC: Int = 10_000

/**
 * The chrome for [session] as [player] plays it at [nowMs] on the device clock. A live service
 * is never seekable here, whatever the engine reports.
 */
fun overlayChrome(session: VideoPlaybackUiState, player: PlayerState, nowMs: Long): OverlayChrome {
    val movie = session.movie
    val service = session.currentService
    val title = session.title.orEmpty()
    return when {
        movie != null -> recordingChrome(movie, title, player)

        service != null -> liveChrome(service, title, nowMs)

        else -> OverlayChrome(
            title = title,
            showInfoButton = false,
            showPvrControls = player.seekable,
            seekable = player.seekable,
            now = null,
            nowEvent = null,
            next = null,
            progress = playerProgress(player)
        )
    }
}

private fun recordingChrome(movie: Movie, title: String, player: PlayerState): OverlayChrome {
    val lengthSec = player.lengthMs / 1000
    val durationSec = if (lengthSec > 0) lengthSec else movieLengthSec(movie.length)
    val now = if (durationSec > 0) {
        OverlayRow(
            start = DateTime.minutesAndSeconds((durationSec * player.position).toLong().toInt()),
            title = movie.serviceName,
            duration = DateTime.minutesAndSeconds(durationSec.toInt())
        )
    } else {
        null
    }
    return OverlayChrome(
        title = movie.title.ifEmpty { title },
        showInfoButton = true,
        showPvrControls = player.seekable,
        seekable = player.seekable,
        now = now,
        nowEvent = null,
        next = null,
        progress = playerProgress(player)
    )
}

/** The player's time on a live stream is the stream's, so the bar follows the programme. */
private fun liveChrome(service: ServiceNowNext, title: String, nowMs: Long): OverlayChrome {
    val now = service.now
    val next = service.next?.takeIf { it.title.isNotEmpty() }
    return OverlayChrome(
        title = service.serviceName.ifEmpty { title },
        showInfoButton = true,
        showPvrControls = false,
        seekable = false,
        now = OverlayRow(
            start = now?.startTimeReadable.orEmpty(),
            title = now?.title.orEmpty(),
            duration = now?.durationReadable.orEmpty()
        ),
        nowEvent = now,
        next = next?.let { OverlayRow(it.startTimeReadable, it.title, it.durationReadable) },
        progress = eventProgress(now, nowMs)?.let {
            OverlayProgress(position = it.elapsedSec.toInt(), max = it.totalSec.toInt())
        }
    )
}

private fun playerProgress(player: PlayerState): OverlayProgress? {
    var lengthSec = player.lengthMs / 1000
    var positionSec = player.timeMs / 1000
    if (lengthSec <= 0 && player.seekable) {
        lengthSec = FAKE_PROGRESS_LENGTH_SEC.toLong()
        positionSec = (lengthSec * player.position).toLong()
    }
    return if (lengthSec > 0 && positionSec >= 0) {
        OverlayProgress(position = positionSec.toInt(), max = lengthSec.toInt())
    } else {
        null
    }
}

/** A movie list's `length`, "minutes:seconds"; 0 when missing or unreadable. */
private fun movieLengthSec(length: String): Long {
    val parts = length.split(":")
    val minutes = parts.getOrNull(0)?.toLongOrNull() ?: return 0
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: return 0
    return minutes * 60 + seconds
}
