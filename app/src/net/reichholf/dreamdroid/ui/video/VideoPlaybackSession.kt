package net.reichholf.dreamdroid.ui.video

import java.io.Serializable
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.video.VideoPlayback

/** What the player shows info for: a live service with now/next, a recording, or nothing. */
sealed interface VideoPlaying {
    data object Unknown : VideoPlaying

    data class Live(val service: ServiceNowNext) : VideoPlaying

    data class Recording(val movie: Movie) : VideoPlaying
}

/**
 * Zap list and playing item behind the player overlay. [services] is the now/next list
 * of [bouquetRef]; [serviceRef] is the zap position in it.
 */
data class VideoPlaybackSession(
    val title: String? = null,
    val serviceRef: String? = null,
    val bouquetRef: String? = null,
    val playing: VideoPlaying = VideoPlaying.Unknown,
    val services: List<ServiceNowNext> = emptyList(),
    val bouquets: List<Service> = emptyList()
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
    ): VideoPlaybackSession {
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
    fun withServices(rows: List<ServiceNowNext>): VideoPlaybackSession {
        val current = rows.lastOrNull { it.serviceReference == serviceRef }
        return copy(
            services = rows,
            playing = if (current != null) VideoPlaying.Live(current) else playing
        )
    }

    fun zappedTo(row: ServiceNowNext): VideoPlaybackSession = copy(
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
