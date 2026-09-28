package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.ServiceRepository
import net.reichholf.dreamdroid.data.movieRepository
import net.reichholf.dreamdroid.data.serviceRepository
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.contentError
import net.reichholf.dreamdroid.enigma.loadMovieList
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService
import net.reichholf.dreamdroid.multiepg.MultiEpgSyncHolder
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder
import net.reichholf.dreamdroid.ui.session.hasUseDrivenCache

data class TvHubBrowseResult(
    val rows: List<HubBouquetRow>,
    val locations: List<String>,
    val errorText: String?,
    val usedCache: Boolean
)

data class TvHubMoviesResult(
    val movies: List<Movie>?,
    val errorText: String?,
    val usedCache: Boolean
)

/**
 * TV hub bouquet + movie-location load. Online writes the use-driven Room
 * cache; Offline paints from it. The `/hdd/movie` location fallback is never
 * a drawer header.
 */
suspend fun loadTvHubBrowse(context: Context): TvHubBrowseResult {
    val app = context.applicationContext
    val profileId = ProfileRepository.get().requireCurrent().id
    val services = serviceRepository(app)
    val movies = movieRepository(app)
    val cachedTabs = services.cachedTvBouquetTabs()
    val cachedMovies = movies.cachedLocations()
    val hasCache = hasUseDrivenCache(
        cachedTabs.map { it.reference },
        hasMovieLocationStrip = cachedMovies != null
    )
    val status = SessionConnectionHolder.shared.status.value
    if (shouldSkipTvHubHttp(status, hasCache)) {
        return paintTvHubFromCache(
            services = services,
            tabs = cachedTabs,
            locations = movieHeadersForTvHub(
                locationsFromReceiver = false,
                liveLocations = emptyList(),
                cachedLocations = cachedMovies
            )
        )
    }
    withContext(Dispatchers.IO) {
        prefetchTvLocationsAndTags()
    }
    val bouquetResult = services.tvBouquetTabs()
    val bouquets = bouquetResult.value
    if (bouquets == null) {
        if (hasCache) {
            return paintTvHubFromCache(
                services = services,
                tabs = cachedTabs,
                locations = movieHeadersForTvHub(
                    locationsFromReceiver = ProfileRepository.get().locationsLoadedFromReceiver(),
                    liveLocations = ProfileRepository.get().locations().toList(),
                    cachedLocations = cachedMovies
                )
            )
        }
        return TvHubBrowseResult(
            rows = emptyList(),
            locations = movieHeadersForTvHub(
                locationsFromReceiver = ProfileRepository.get().locationsLoadedFromReceiver(),
                liveLocations = ProfileRepository.get().locations().toList(),
                cachedLocations = cachedMovies
            ),
            errorText = bouquetResult.error.contentError(app),
            usedCache = false
        )
    }
    val rows = ArrayList<HubBouquetRow>()
    var lastError: String? = null
    val nowSec = System.currentTimeMillis() / 1000L
    val sync = MultiEpgSyncHolder.shared(app)
    for (bouquet in bouquets) {
        val ref = bouquet.reference
        if (ref.isBlank()) {
            continue
        }
        val loaded = services.receiverNowNext(ref)
        val loadedRows = loaded.value
        if (loadedRows != null) {
            val serviceRows = withoutBouquetSpacers(loadedRows)
            rows.add(HubBouquetRow(bouquet = bouquet, services = serviceRows))
            if (profileId != null && services.persistRoster(ref, ref, serviceRows)) {
                try {
                    sync.ensureChunk(profileId, ref, nowSec, persist = true)
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) {
                        throw t
                    }
                    Log.w(DreamDroid.LOG_TAG, "TV hub epgmulti fill failed", t)
                }
            }
            continue
        }
        lastError = loaded.error.contentError(app)
        val cached = paintBouquetFromCache(services, bouquet, nowSec)
        if (cached != null) {
            rows.add(cached)
        }
    }
    if (ProfileRepository.get().locationsLoadedFromReceiver()) {
        movies.saveLocations(ProfileRepository.get().locations().toList())
    }
    val locations = movieHeadersForTvHub(
        locationsFromReceiver = ProfileRepository.get().locationsLoadedFromReceiver(),
        liveLocations = ProfileRepository.get().locations().toList(),
        cachedLocations = cachedMovies
    )
    return TvHubBrowseResult(
        rows = rows,
        locations = locations,
        errorText = if (rows.isEmpty()) lastError else null,
        usedCache = false
    )
}

suspend fun loadTvHubMovies(context: Context, dirname: String): TvHubMoviesResult {
    val app = context.applicationContext
    val movies = movieRepository(app)
    val cached = movies.cachedMovies(dirname)
    val status = SessionConnectionHolder.shared.status.value
    val hasCache = cached != null
    if (shouldSkipTvHubHttp(status, hasCache)) {
        return TvHubMoviesResult(
            movies = cached.orEmpty(),
            errorText = null,
            usedCache = true
        )
    }
    val result = loadMovieList(app, listOf(NameValuePair("dirname", dirname)))
    if (result.success) {
        movies.saveMovies(dirname, result.movies)
        return TvHubMoviesResult(
            movies = result.movies,
            errorText = null,
            usedCache = false
        )
    }
    if (cached != null) {
        return TvHubMoviesResult(
            movies = cached,
            errorText = null,
            usedCache = true
        )
    }
    return TvHubMoviesResult(
        movies = null,
        errorText = result.errorText,
        usedCache = false
    )
}

internal fun prefetchTvLocationsAndTags() {
    val http = EnigmaHttp()
    if (ProfileRepository.get().locations().size <= 1) {
        if (!ProfileRepository.get().loadLocations(http)) {
            Log.e(DreamDroid.LOG_TAG, "ERROR loading locations")
        }
    }
    if (ProfileRepository.get().tags().size <= 1) {
        if (!ProfileRepository.get().loadTags(http)) {
            Log.e(DreamDroid.LOG_TAG, "ERROR loading tags")
        }
    }
}

private suspend fun paintTvHubFromCache(
    services: ServiceRepository,
    tabs: List<Service>,
    locations: List<String>
): TvHubBrowseResult {
    val nowSec = System.currentTimeMillis() / 1000L
    val rows = tabs.mapNotNull { bouquet -> paintBouquetFromCache(services, bouquet, nowSec) }
    return TvHubBrowseResult(
        rows = rows,
        locations = locations,
        errorText = null,
        usedCache = true
    )
}

private suspend fun paintBouquetFromCache(
    services: ServiceRepository,
    bouquet: Service,
    nowSec: Long
): HubBouquetRow? {
    val ref = bouquet.reference
    if (ref.isBlank()) {
        return null
    }
    val cached = services.cachedNowNext(ref, nowSec) ?: return null
    return HubBouquetRow(bouquet = bouquet, services = withoutBouquetSpacers(cached))
}

/** Drop Enigma2 bouquet spacers (`1:832:`) from a TV hub row. `1:64:` markers stay. */
internal fun withoutBouquetSpacers(services: List<ServiceNowNext>): List<ServiceNowNext> =
    services.filterNot { service -> EnigmaService.isSpacer(service.serviceReference) }

internal fun unavailableTvHubMessage(
    usedCache: Boolean,
    paintedRows: Boolean,
    errorText: String?
): String? = if (!usedCache && !paintedRows) errorText else null
