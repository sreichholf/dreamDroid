package net.reichholf.dreamdroid.data

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.EnigmaClientFactory
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.EnigmaUrls
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.Movie as MovieKeys
import net.reichholf.dreamdroid.helpers.enigma2.Tag
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.MovieLocationStripEntity
import net.reichholf.dreamdroid.room.toListEntity
import net.reichholf.dreamdroid.room.toMovie

/** Outcome of a movie list load. */
sealed interface MovieListLoad {
    /** Movies to show. [cached] ones come from the Room snapshot of that location. */
    data class Movies(val movies: List<Movie>, val cached: Boolean) : MovieListLoad

    /** The receiver failed and Room has nothing to show instead. */
    data class Failed(val error: EnigmaHttpError?) : MovieListLoad
}

/** Outcome of copying a recording into the app cache. */
sealed interface MovieDownload {
    data class Ready(val file: File) : MovieDownload

    data class Failed(val error: EnigmaHttpError) : MovieDownload
}

/**
 * Recordings of the active profile. Snapshots are use-driven (docs/offline-and-errors.md):
 * the location names are written when the receiver answered the locations request, and a
 * location's `/web/movielist` when that location was opened without a tag filter. Play and
 * delete stay Online-only; callers gate them.
 */
@Singleton
class MovieRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clients: EnigmaClientFactory,
    private val profiles: ProfileRepository,
    private val database: AppDatabase
) {
    /**
     * Movies in [location] (the receiver's default location when empty) filtered by [tags].
     * An unfiltered answer replaces the snapshot of that location. When the receiver fails,
     * an unfiltered load falls back to the snapshot; a filtered one never paints the
     * unfiltered snapshot.
     */
    suspend fun movies(location: String, tags: List<String>): MovieListLoad {
        val params = ArrayList<NameValuePair>()
        if (location.isNotEmpty()) {
            params.add(NameValuePair("dirname", location))
        }
        if (tags.isNotEmpty()) {
            params.add(NameValuePair("tag", Tag.implodeTags(ArrayList(tags))))
        }
        val response = clients.current().getMovies(params)
        val live = response.value
        if (live != null) {
            if (tags.isEmpty()) {
                saveMovies(location, live)
            }
            return MovieListLoad.Movies(live, cached = false)
        }
        val cached = if (tags.isEmpty()) cachedMovies(location) else null
        return if (cached != null) {
            MovieListLoad.Movies(cached, cached = true)
        } else {
            MovieListLoad.Failed(response.error)
        }
    }

    suspend fun delete(movie: Movie): EnigmaResponse<SimpleResult> =
        clients.current().deleteMovie(MovieKeys.getDeleteParams(movie))

    /**
     * A URL any viewer can open for the recording at [remotePath], or null when the profile
     * needs a login and the file has to go through [downloadToCache].
     */
    fun directLink(remotePath: String): String? {
        val profile = profiles.requireCurrent()
        if (profile.login) {
            return null
        }
        return EnigmaUrls.page(profile, URIStore.FILE, listOf(NameValuePair("file", remotePath)))
    }

    /** Copies the recording at [remotePath] into the app cache, with the profile's login. */
    suspend fun downloadToCache(remotePath: String): MovieDownload {
        val profile = profiles.requireCurrent()
        val out = File(context.cacheDir, movieCacheFileName(remotePath))
        val params = listOf(NameValuePair("file", remotePath))
        val fetched = withContext(Dispatchers.IO) {
            EnigmaHttp(profile).downloadToFile(URIStore.FILE, params, out)
        }
        return when (fetched) {
            is EnigmaHttpResult.Failure -> {
                out.delete()
                MovieDownload.Failed(fetched.error)
            }

            is EnigmaHttpResult.Success -> MovieDownload.Ready(out)
        }
    }

    /**
     * The receiver's locations when it answered ([receiverAnswered]), which also replace
     * the snapshot. Otherwise the snapshot, or [live] when there is none.
     */
    suspend fun locationsOrCached(receiverAnswered: Boolean, live: List<String>): List<String> {
        if (receiverAnswered) {
            saveLocations(live)
            return live
        }
        return cachedLocations() ?: live
    }

    /**
     * Snapshot of the location names, or null when this profile never wrote one. Empty
     * means the receiver answered with no locations.
     */
    suspend fun cachedLocations(): List<String>? {
        val profileId = profiles.requireCurrent().id ?: return null
        val dao = database.movieDao()
        if (dao.locationMetaCount(profileId) == 0) {
            return null
        }
        return dao.getLocationStrip(profileId).map { it.dirname }
    }

    suspend fun saveLocations(locations: List<String>) {
        val profileId = profiles.requireCurrent().id ?: return
        database.movieDao().replaceLocations(
            profileId,
            locations.mapIndexed { index, dirname ->
                MovieLocationStripEntity(profileId, index, dirname)
            }
        )
    }

    /**
     * Snapshot of [location], or null when it was never written. Empty means the receiver
     * answered with no movies.
     */
    suspend fun cachedMovies(location: String): List<Movie>? {
        val profileId = profiles.requireCurrent().id ?: return null
        val dao = database.movieDao()
        if (dao.movieListMetaCount(profileId, location) == 0) {
            return null
        }
        return dao.getMovieList(profileId, location).map { it.toMovie() }
    }

    suspend fun saveMovies(location: String, movies: List<Movie>) {
        val profileId = profiles.requireCurrent().id ?: return
        database.movieDao().replaceMovies(
            profileId,
            location,
            movies.mapIndexed { index, movie -> movie.toListEntity(profileId, location, index) }
        )
    }
}

private fun movieCacheFileName(remotePath: String): String {
    val base = remotePath.substringAfterLast('/')
    if (base.isEmpty() || base == "." || base == "..") {
        return "movie"
    }
    return base
}

/**
 * Transitional lookup for the TV hub browse load, which is not Hilt-injected yet. It runs
 * in an activity, after `DreamDroid` was injected. Delete with the last caller (PR 12 in
 * docs/hilt-migration.md).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MovieRepositoryEntryPoint {
    fun movieRepository(): MovieRepository
}

fun movieRepository(context: Context): MovieRepository = EntryPointAccessors
    .fromApplication(context.applicationContext, MovieRepositoryEntryPoint::class.java)
    .movieRepository()
