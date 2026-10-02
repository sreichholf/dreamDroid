package net.reichholf.dreamdroid.ui.video

import java.io.Serializable
import net.reichholf.dreamdroid.data.LiveStream
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.ui.text.UiText
import net.reichholf.dreamdroid.video.VideoPlayback

/** What the player shows info for: a live service with now/next, a recording, or nothing. */
sealed interface VideoPlaying {
    data object Unknown : VideoPlaying

    data class Live(val service: ServiceNowNext) : VideoPlaying

    data class Recording(val movie: Movie) : VideoPlaying
}

/**
 * The player's sleep timer. [Running] counts minutes, [Closing] the last
 * [SLEEP_TIMER_WARNING_SECONDS] before [Expired] closes the player.
 */
sealed interface SleepTimer {
    data object Off : SleepTimer

    data class Running(val minutesLeft: Int) : SleepTimer

    data class Closing(val secondsLeft: Int) : SleepTimer

    data object Expired : SleepTimer
}

/** Durations the sleep timer offers. */
val SLEEP_TIMER_MINUTES: List<Int> = listOf(15, 30, 45, 60, 90, 120)

const val SLEEP_TIMER_WARNING_SECONDS: Int = 10

/** What the countdown's extend button restarts the timer with. */
const val SLEEP_TIMER_EXTEND_MINUTES: Int = 15

/**
 * Zap list and playing item behind the player overlay. [services] is the now/next list
 * of [bouquetRef]; [serviceRef] is the zap position in it. [stream] is a zapped-to
 * service that may stream now: the overlay plays it and reports that back.
 */
data class VideoPlaybackUiState(
    val title: String? = null,
    val serviceRef: String? = null,
    val bouquetRef: String? = null,
    val playing: VideoPlaying = VideoPlaying.Unknown,
    val services: List<ServiceNowNext> = emptyList(),
    val bouquets: List<Service> = emptyList(),
    val stream: LiveStream.Ready? = null,
    val sleepTimer: SleepTimer = SleepTimer.Off,
    val userMessage: UiText? = null
) {
    val movie: Movie? get() = (playing as? VideoPlaying.Recording)?.movie

    val currentService: ServiceNowNext? get() = (playing as? VideoPlaying.Live)?.service

    val currentIndex: Int get() = services.indexOfFirst { it.serviceReference == serviceRef }

    /** Refs and title from new ACTION_VIEW extras. [info] is the extras' service info. */
    fun withExtras(
        title: String?,
        serviceRef: String?,
        bouquetRef: String?,
        info: Serializable?
    ): VideoPlaybackUiState {
        val refsChanged = serviceRef != this.serviceRef || bouquetRef != this.bouquetRef
        val playing = when (info) {
            is Movie -> VideoPlaying.Recording(info)
            is ServiceNowNext -> VideoPlaying.Live(info)
            else -> if (refsChanged) VideoPlaying.Unknown else playing
        }
        return copy(
            title = title,
            serviceRef = serviceRef,
            bouquetRef = bouquetRef,
            playing = playing,
            bouquets = if (playing is VideoPlaying.Recording) emptyList() else bouquets
        )
    }

    /** A fresh now/next list. The row at [serviceRef] becomes the playing service. */
    fun withServices(rows: List<ServiceNowNext>): VideoPlaybackUiState {
        val current = rows.lastOrNull { it.serviceReference == serviceRef }
        return copy(
            services = rows,
            playing = if (current != null) VideoPlaying.Live(current) else playing
        )
    }

    fun zappedTo(row: ServiceNowNext): VideoPlaybackUiState = copy(
        serviceRef = row.serviceReference,
        title = row.serviceName,
        playing = VideoPlaying.Live(row)
    )

    /** The neighbour of [currentIndex] in [services], wrapping; null when there is none. */
    fun neighbour(forward: Boolean): ServiceNowNext? {
        val index = if (forward) {
            VideoPlayback.nextIndex(currentIndex, services.size)
        } else {
            VideoPlayback.previousIndex(currentIndex, services.size)
        }
        return services.getOrNull(index)
    }
}
