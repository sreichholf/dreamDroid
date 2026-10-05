package net.reichholf.dreamdroid.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.enigma.Movie
import net.reichholf.dreamdroid.enigma.ReceiverApiFactory
import net.reichholf.dreamdroid.enigma.SimpleResult
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.MovieLocationStripEntity
import net.reichholf.dreamdroid.room.toListEntity
import net.reichholf.dreamdroid.room.toMovie
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/** Outcome of a movie list load. */
sealed interface MovieListLoad {
    /** Movies to show. [cached] ones come from the Room snapshot of that location. */
    data class Movies(val movies: List<Movie>, val cached: Boolean) : MovieListLoad

    /** The receiver failed and Room has nothing to show instead. */
    data class Failed(val error: EnigmaHttpError?) : MovieListLoad
}

/** Movie location names; [fromReceiver] is false for Room's strip and the `/hdd/movie` stand-in. */
data class MovieLocations(val names: List<String>, val fromReceiver: Boolean)

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
    private val clients: ReceiverApiFactory,
    private val profiles: ProfileRepository,
    private val database: AppDatabase,
    private val sessions: SessionConnectionHolder,
    private val timers: TimerRepository
) {
    /**
     * Movies in [location] (the receiver's default location when empty) filtered by [tags],
     * read [cacheFirstLoad]. Only an unfiltered load reads Room, and its answer replaces the
     * snapshot of that location; a filtered one never paints the unfiltered snapshot.
     */
    fun movies(
        location: String,
        tags: List<String>,
        forceRefresh: Boolean = false
    ): Flow<MovieListLoad> = cacheFirstLoad(
        sessions,
        forceRefresh,
        cached = { if (tags.isEmpty()) cachedMovies(location) else null },
        fetch = {
            clients.current().movies(location, tags).also { response ->
                val live = response.value
                if (live != null && tags.isEmpty()) {
                    saveMovies(location, live)
                }
            }
        },
        loaded = MovieListLoad::Movies,
        failed = MovieListLoad::Failed
    )

    suspend fun delete(movie: Movie): EnigmaResponse<SimpleResult> =
        clients.current().deleteMovie(movie)

    /** The stream of [movie] from the active profile, for the video player. */
    fun streamUrl(movie: Movie): String = clients.current().recordingStreamUrl(movie)

    /**
     * A URL any viewer can open for the recording at [remotePath], or null when the profile
     * needs a login and the file has to go through [downloadToCache].
     */
    fun directLink(remotePath: String): String? {
        val profile = profiles.requireCurrent()
        if (profile.login) {
            return null
        }
        return clients.forProfile(profile).recordingFileUrl(remotePath)
    }

    /** Copies the recording at [remotePath] into the app cache, with the profile's login. */
    suspend fun downloadToCache(remotePath: String): MovieDownload {
        val api = clients.current()
        return withContext(Dispatchers.IO) {
            val out = File(context.cacheDir, movieCacheFileName(remotePath))
            val error = api.downloadRecording(remotePath, out)
            if (error != null) {
                out.delete()
                MovieDownload.Failed(error)
            } else {
                MovieDownload.Ready(out)
            }
        }
    }

    /**
     * The movie locations: Room's location strip first, then [TimerRepository.locationsAndTags],
     * which does not ask the receiver while the session is Offline. The receiver's answer
     * replaces the strip. Without one the strip stays, or the `/hdd/movie` stand-in paints when
     * there is no strip.
     */
    fun locations(): Flow<MovieLocations> = flow {
        val cached = cachedLocations()
        if (cached != null) {
            emit(MovieLocations(cached, fromReceiver = false))
        }
        val choices = timers.locationsAndTags()
        if (choices.locationsFromReceiver) {
            saveLocations(choices.locations)
            emit(MovieLocations(choices.locations, fromReceiver = true))
        } else if (cached == null) {
            emit(MovieLocations(choices.locations, fromReceiver = false))
        }
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
