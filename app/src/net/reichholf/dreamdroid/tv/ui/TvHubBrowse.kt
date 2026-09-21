package net.reichholf.dreamdroid.tv.ui

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.Service
import net.reichholf.dreamdroid.enigma.ServiceNowNext
import net.reichholf.dreamdroid.enigma.loadEpgNowNext
import net.reichholf.dreamdroid.enigma.loadMovieList
import net.reichholf.dreamdroid.enigma.loadServiceList
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Service as EnigmaService
import net.reichholf.dreamdroid.multiepg.MultiEpgSyncHolder
import net.reichholf.dreamdroid.multiepg.MultiEpgWindows
import net.reichholf.dreamdroid.multiepg.UserBouquetEpgFill
import net.reichholf.dreamdroid.multiepg.overlayNowNext
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.EpgDao
import net.reichholf.dreamdroid.room.MovieSnapshotStore
import net.reichholf.dreamdroid.room.RosterDao
import net.reichholf.dreamdroid.room.UserBouquetCache
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
    val profileId = DreamDroid.getCurrentProfile().id
    val excluded = UserBouquetCache.excludedHubTabRefs(app)
    val db = AppDatabase.database(app)
    val rosterDao = db.rosterDao()
    val epgDao = db.epgDao()
    val movieDao = db.movieDao()
    val cachedTabs = if (profileId != null) {
        UserBouquetCache.loadTabStripServices(
            rosterDao,
            profileId,
            UserBouquetCache.KIND_TV
        )
    } else {
        emptyList()
    }
    val cachedMovies = if (profileId != null) {
        MovieSnapshotStore.loadLocations(movieDao, profileId)
    } else {
        null
    }
    val hasCache = hasUseDrivenCache(
        cachedTabs.map { it.reference },
        hasMovieLocationStrip = cachedMovies != null
    )
    val status = SessionConnectionHolder.shared.status.value
    if (shouldSkipTvHubHttp(status, hasCache)) {
        return paintTvHubFromCache(
            rosterDao = rosterDao,
            epgDao = epgDao,
            profileId = profileId,
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
    val bouquetResult = loadServiceList(
        app,
        listOf(NameValuePair("bRef", TvComposeHubHost.BOUQUETS_TV))
    )
    if (!bouquetResult.success) {
        if (hasCache) {
            return paintTvHubFromCache(
                rosterDao = rosterDao,
                epgDao = epgDao,
                profileId = profileId,
                tabs = cachedTabs,
                locations = movieHeadersForTvHub(
                    locationsFromReceiver = DreamDroid.locationsLoadedFromReceiver(),
                    liveLocations = DreamDroid.getLocations().toList(),
                    cachedLocations = cachedMovies
                )
            )
        }
        return TvHubBrowseResult(
            rows = emptyList(),
            locations = movieHeadersForTvHub(
                locationsFromReceiver = DreamDroid.locationsLoadedFromReceiver(),
                liveLocations = DreamDroid.getLocations().toList(),
                cachedLocations = cachedMovies
            ),
            errorText = bouquetResult.errorText,
            usedCache = false
        )
    }
    if (profileId != null) {
        UserBouquetCache.replaceTabStrip(
            rosterDao,
            profileId,
            UserBouquetCache.KIND_TV,
            bouquetResult.services,
            excluded
        )
    }
    val rows = ArrayList<HubBouquetRow>()
    var lastError: String? = null
    val nowSec = System.currentTimeMillis() / 1000L
    val sync = MultiEpgSyncHolder.shared(app)
    for (bouquet in bouquetResult.services) {
        val ref = bouquet.reference
        if (ref.isBlank()) {
            continue
        }
        val epg = loadEpgNowNext(app, listOf(NameValuePair("bRef", ref)))
        if (epg.success) {
            rows.add(
                HubBouquetRow(
                    bouquet = bouquet,
                    services = withoutBouquetSpacers(epg.rows)
                )
            )
            if (profileId != null) {
                UserBouquetCache.persistRosterIfCacheable(
                    dao = rosterDao,
                    profileId = profileId,
                    ref = ref,
                    tabRootRef = ref,
                    rows = epg.rows,
                    excludedTabRefs = excluded
                )
                try {
                    UserBouquetEpgFill.ensureNowChunk(
                        sync = sync,
                        rosterDao = rosterDao,
                        profileId = profileId,
                        containerRef = ref,
                        tabRootRef = ref,
                        excludedTabRefs = excluded,
                        unixSec = nowSec
                    )
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) {
                        throw t
                    }
                    Log.w(DreamDroid.LOG_TAG, "TV hub epgmulti fill failed", t)
                }
            }
            continue
        }
        lastError = epg.errorText
        val cached = paintBouquetFromCache(
            rosterDao = rosterDao,
            epgDao = epgDao,
            profileId = profileId,
            bouquet = bouquet,
            nowSec = nowSec
        )
        if (cached != null) {
            rows.add(cached)
        }
    }
    if (profileId != null && DreamDroid.locationsLoadedFromReceiver()) {
        MovieSnapshotStore.replaceLocations(
            movieDao,
            profileId,
            DreamDroid.getLocations().toList()
        )
    }
    val locations = movieHeadersForTvHub(
        locationsFromReceiver = DreamDroid.locationsLoadedFromReceiver(),
        liveLocations = DreamDroid.getLocations().toList(),
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
    val profileId = DreamDroid.getCurrentProfile().id
    val movieDao = AppDatabase.movie(app)
    val cached = if (profileId != null) {
        MovieSnapshotStore.loadMovies(movieDao, profileId, dirname)
    } else {
        null
    }
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
        if (profileId != null) {
            MovieSnapshotStore.replaceMovies(movieDao, profileId, dirname, result.movies)
        }
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
    if (DreamDroid.getLocations().size <= 1) {
        if (!DreamDroid.loadLocations(http)) {
            Log.e(DreamDroid.LOG_TAG, "ERROR loading locations")
        }
    }
    if (DreamDroid.getTags().size <= 1) {
        if (!DreamDroid.loadTags(http)) {
            Log.e(DreamDroid.LOG_TAG, "ERROR loading tags")
        }
    }
}

private suspend fun paintTvHubFromCache(
    rosterDao: RosterDao,
    epgDao: EpgDao,
    profileId: Int?,
    tabs: List<Service>,
    locations: List<String>
): TvHubBrowseResult {
    val nowSec = System.currentTimeMillis() / 1000L
    val rows = tabs.mapNotNull { bouquet ->
        paintBouquetFromCache(
            rosterDao = rosterDao,
            epgDao = epgDao,
            profileId = profileId,
            bouquet = bouquet,
            nowSec = nowSec
        )
    }
    return TvHubBrowseResult(
        rows = rows,
        locations = locations,
        errorText = null,
        usedCache = true
    )
}

private suspend fun paintBouquetFromCache(
    rosterDao: RosterDao,
    epgDao: EpgDao,
    profileId: Int?,
    bouquet: Service,
    nowSec: Long
): HubBouquetRow? {
    val pid = profileId ?: return null
    val ref = bouquet.reference
    if (ref.isBlank()) {
        return null
    }
    val cached = UserBouquetCache.loadRosterNowNext(rosterDao, pid, ref) ?: return null
    val chunk = MultiEpgWindows.chunkContaining(nowSec)
    val events = epgDao.eventsOverlapping(pid, ref, chunk.startSec, chunk.endSec)
    return HubBouquetRow(
        bouquet = bouquet,
        services = withoutBouquetSpacers(overlayNowNext(cached, events, nowSec))
    )
}

/** Drop Enigma2 bouquet spacers (`1:832:`) from a TV hub row. `1:64:` markers stay. */
internal fun withoutBouquetSpacers(services: List<ServiceNowNext>): List<ServiceNowNext> =
    services.filterNot { service -> EnigmaService.isSpacer(service.serviceReference) }

internal fun unavailableTvHubMessage(
    usedCache: Boolean,
    paintedRows: Boolean,
    errorText: String?
): String? = if (!usedCache && !paintedRows) errorText else null
