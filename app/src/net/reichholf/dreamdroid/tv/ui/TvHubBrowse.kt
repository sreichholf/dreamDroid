package net.reichholf.dreamdroid.tv.ui

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.data.EpgRepository
import net.reichholf.dreamdroid.data.MovieListLoad
import net.reichholf.dreamdroid.data.MovieRepository
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.TimerRepository
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.contentErrorText
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.text.UiText

data class TvHubBrowseResult(
    val rows: List<HubBouquetRow>,
    val locations: List<String>,
    val errorText: UiText?,
    val usedCache: Boolean
)

data class TvHubMoviesResult(
    val movies: List<Movie>?,
    val errorText: UiText?,
    val usedCache: Boolean
)

/**
 * TV hub bouquet rows and movie locations of the active profile. Online writes the
 * use-driven Room cache; Offline paints from it. The `/hdd/movie` location fallback is
 * never a drawer header.
 */
class TvHubBrowse @Inject constructor(
    private val services: ServiceRepository,
    private val epg: EpgRepository,
    private val movies: MovieRepository,
    private val timers: TimerRepository,
    private val profiles: ProfileRepository,
    private val sessions: SessionConnectionHolder
) {
    /**
     * Each TV bouquet with now/next. Room paints first; the receiver is skipped while the
     * session is Offline and Room had something to paint. A bouquet the receiver answered
     * replaces its Room roster and fills the MultiEPG chunk at now; one it failed paints
     * from Room.
     */
    fun browse(): Flow<TvHubBrowseResult> = flow {
        val cachedTabs = services.cachedTvBouquetTabs()
        val cachedLocations = movies.cachedLocations()
        val hasCache = cachedTabs.isNotEmpty() || cachedLocations != null
        if (hasCache) {
            emit(
                paintFromCache(
                    tabs = cachedTabs,
                    locations = movieHeadersForTvHub(
                        locationsFromReceiver = false,
                        liveLocations = emptyList(),
                        cachedLocations = cachedLocations
                    )
                )
            )
        }
        if (sessions.status.value.shouldSkipReceiverHttp(hasCache)) {
            return@flow
        }
        emit(receiverBrowse(cachedTabs, cachedLocations))
    }

    private suspend fun receiverBrowse(
        cachedTabs: List<Service>,
        cachedLocations: List<String>?
    ): TvHubBrowseResult {
        timers.locationsAndTags()
        val bouquetResult = services.tvBouquetTabs()
        val bouquets = bouquetResult.value
        if (bouquets == null) {
            val locations = liveMovieHeaders(cachedLocations)
            if (cachedTabs.isNotEmpty() || cachedLocations != null) {
                return paintFromCache(cachedTabs, locations)
            }
            return TvHubBrowseResult(
                rows = emptyList(),
                locations = locations,
                errorText = bouquetResult.error.contentErrorText(),
                usedCache = false
            )
        }
        val rows = ArrayList<HubBouquetRow>()
        var lastError: UiText? = null
        val nowSec = System.currentTimeMillis() / 1000L
        for (bouquet in bouquets) {
            val ref = bouquet.reference
            if (ref.isBlank()) {
                continue
            }
            val response = services.receiverNowNext(ref)
            val loaded = response.value
            if (loaded != null) {
                val serviceRows = withoutBouquetSpacers(loaded)
                rows.add(HubBouquetRow(bouquet = bouquet, services = serviceRows))
                if (services.persistRoster(ref, ref, serviceRows)) {
                    fillNowChunk(ref, nowSec)
                }
                continue
            }
            lastError = response.error.contentErrorText()
            paintBouquetFromCache(bouquet, nowSec)?.let { rows.add(it) }
        }
        if (profiles.locationsLoadedFromReceiver()) {
            movies.saveLocations(profiles.locations().toList())
        }
        return TvHubBrowseResult(
            rows = rows,
            locations = liveMovieHeaders(cachedLocations),
            errorText = if (rows.isEmpty()) lastError else null,
            usedCache = false
        )
    }

    /** Recordings in [dirname]; see [MovieRepository.movies]. */
    fun movies(dirname: String): Flow<TvHubMoviesResult> =
        movies.movies(dirname, emptyList()).map { load ->
            when (load) {
                is MovieListLoad.Movies ->
                    TvHubMoviesResult(
                        movies = load.movies,
                        errorText = null,
                        usedCache = load.cached
                    )

                is MovieListLoad.Failed ->
                    TvHubMoviesResult(
                        movies = null,
                        errorText = load.error.contentErrorText(),
                        usedCache = false
                    )
            }
        }

    private suspend fun fillNowChunk(ref: String, nowSec: Long) {
        try {
            epg.fillNowChunk(ref, ref, nowSec)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(DreamDroid.LOG_TAG, "TV hub epgmulti fill failed", t)
        }
    }

    private fun liveMovieHeaders(cachedLocations: List<String>?): List<String> =
        movieHeadersForTvHub(
            locationsFromReceiver = profiles.locationsLoadedFromReceiver(),
            liveLocations = profiles.locations().toList(),
            cachedLocations = cachedLocations
        )

    private suspend fun paintFromCache(
        tabs: List<Service>,
        locations: List<String>
    ): TvHubBrowseResult {
        val nowSec = System.currentTimeMillis() / 1000L
        return TvHubBrowseResult(
            rows = tabs.mapNotNull { bouquet -> paintBouquetFromCache(bouquet, nowSec) },
            locations = locations,
            errorText = null,
            usedCache = true
        )
    }

    private suspend fun paintBouquetFromCache(bouquet: Service, nowSec: Long): HubBouquetRow? {
        val ref = bouquet.reference
        if (ref.isBlank()) {
            return null
        }
        val cached = services.cachedNowNext(ref, nowSec) ?: return null
        return HubBouquetRow(bouquet = bouquet, services = withoutBouquetSpacers(cached))
    }
}

/** Drop Enigma2 bouquet spacers (`1:832:`) from a TV hub row. `1:64:` markers stay. */
internal fun withoutBouquetSpacers(services: List<ServiceNowNext>): List<ServiceNowNext> =
    services.filterNot { service -> EnigmaService.isSpacer(service.serviceReference) }

internal fun unavailableTvHubMessage(
    usedCache: Boolean,
    paintedRows: Boolean,
    errorText: UiText?
): UiText? = if (!usedCache && !paintedRows) errorText else null
