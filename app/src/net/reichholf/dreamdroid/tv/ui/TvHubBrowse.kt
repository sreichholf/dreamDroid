package net.reichholf.dreamdroid.tv.ui

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
     * Each TV bouquet with now/next. A bouquet the receiver answered replaces its Room
     * roster and fills the MultiEPG chunk at now; one it failed paints from Room.
     */
    suspend fun browse(): TvHubBrowseResult {
        val cachedTabs = services.cachedTvBouquetTabs()
        val cachedLocations = movies.cachedLocations()
        val hasCache = cachedTabs.isNotEmpty() || cachedLocations != null
        if (shouldSkipTvHubHttp(sessions.status.value, hasCache)) {
            return paintFromCache(
                tabs = cachedTabs,
                locations = movieHeadersForTvHub(
                    locationsFromReceiver = false,
                    liveLocations = emptyList(),
                    cachedLocations = cachedLocations
                )
            )
        }
        timers.locationsAndTags()
        val bouquetResult = services.tvBouquetTabs()
        val bouquets = bouquetResult.value
        if (bouquets == null) {
            val locations = liveMovieHeaders(cachedLocations)
            if (hasCache) {
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

    /**
     * Recordings in [dirname]. Room answers without asking the receiver while the session
     * is not Online and Room has that location, and when the receiver fails.
     */
    suspend fun movies(dirname: String): TvHubMoviesResult {
        val cached = movies.cachedMovies(dirname)
        if (shouldSkipTvHubHttp(sessions.status.value, cached != null)) {
            return TvHubMoviesResult(movies = cached.orEmpty(), errorText = null, usedCache = true)
        }
        return when (val load = movies.movies(dirname, emptyList())) {
            is MovieListLoad.Movies ->
                TvHubMoviesResult(movies = load.movies, errorText = null, usedCache = load.cached)

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
