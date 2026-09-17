package net.reichholf.dreamdroid.room

import net.reichholf.dreamdroid.enigma.Movie

/**
 * Use-driven movie snapshots. The hub Movies tab writes location names on a
 * successful locations load; each opened dirname writes its `/web/movielist`
 * on HTTP success. Play/delete stay Online-only.
 */
object MovieSnapshotStore {
    suspend fun replaceLocations(dao: MovieDao, profileId: Int, locations: List<String>) {
        dao.replaceLocations(
            profileId,
            locations.mapIndexed { index, dirname ->
                MovieLocationStripEntity(profileId, index, dirname)
            }
        )
    }

    /**
     * Cached movie location names for [profileId], or null if this profile's
     * Movies tab was never written. Empty list means we did write and locations
     * HTTP had no rows.
     */
    suspend fun loadLocations(dao: MovieDao, profileId: Int): List<String>? {
        if (dao.locationMetaCount(profileId) == 0) {
            return null
        }
        return dao.getLocationStrip(profileId).map { it.dirname }
    }

    suspend fun replaceMovies(dao: MovieDao, profileId: Int, dirname: String, movies: List<Movie>) {
        dao.replaceMovies(
            profileId,
            dirname,
            movies.mapIndexed { index, movie ->
                movie.toListEntity(profileId, dirname, index)
            }
        )
    }

    /**
     * Cached movielist for [profileId] + [dirname], or null if that dirname was
     * never written. Empty list means we did write and `/web/movielist` had no
     * rows.
     */
    suspend fun loadMovies(dao: MovieDao, profileId: Int, dirname: String): List<Movie>? {
        if (dao.movieListMetaCount(profileId, dirname) == 0) {
            return null
        }
        return dao.getMovieList(profileId, dirname).map { it.toMovie() }
    }
}
