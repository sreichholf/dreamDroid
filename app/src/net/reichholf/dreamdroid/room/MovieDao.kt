package net.reichholf.dreamdroid.room

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
interface MovieDao {
    @Query("DELETE FROM movie_location_strip WHERE profileId = :profileId")
    suspend fun deleteLocationStrip(profileId: Int)

    @Query("DELETE FROM movie_location_meta WHERE profileId = :profileId")
    suspend fun deleteLocationMeta(profileId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationStrip(rows: List<MovieLocationStripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationMeta(meta: MovieLocationMetaEntity)

    @Transaction
    suspend fun replaceLocations(profileId: Int, rows: List<MovieLocationStripEntity>) {
        deleteLocationStrip(profileId)
        deleteLocationMeta(profileId)
        insertLocationMeta(MovieLocationMetaEntity(profileId))
        if (rows.isNotEmpty()) {
            insertLocationStrip(rows)
        }
    }

    @Query(
        """
        SELECT * FROM movie_location_strip
        WHERE profileId = :profileId
        ORDER BY position ASC
        """
    )
    suspend fun getLocationStrip(profileId: Int): List<MovieLocationStripEntity>

    @Query(
        """
        SELECT COUNT(*) FROM movie_location_meta
        WHERE profileId = :profileId
        """
    )
    suspend fun locationMetaCount(profileId: Int): Int

    @Query(
        """
        DELETE FROM movie_list
        WHERE profileId = :profileId AND dirname = :dirname
        """
    )
    suspend fun deleteMovieRows(profileId: Int, dirname: String)

    @Query(
        """
        DELETE FROM movie_list_meta
        WHERE profileId = :profileId AND dirname = :dirname
        """
    )
    suspend fun deleteMovieListMeta(profileId: Int, dirname: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovieRows(rows: List<MovieListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovieListMeta(meta: MovieListMetaEntity)

    @Transaction
    suspend fun replaceMovies(profileId: Int, dirname: String, rows: List<MovieListEntity>) {
        deleteMovieRows(profileId, dirname)
        deleteMovieListMeta(profileId, dirname)
        insertMovieListMeta(MovieListMetaEntity(profileId, dirname))
        if (rows.isNotEmpty()) {
            insertMovieRows(rows)
        }
    }

    @Query(
        """
        SELECT * FROM movie_list
        WHERE profileId = :profileId AND dirname = :dirname
        ORDER BY position ASC
        """
    )
    suspend fun getMovieList(profileId: Int, dirname: String): List<MovieListEntity>

    @Query(
        """
        SELECT COUNT(*) FROM movie_list_meta
        WHERE profileId = :profileId AND dirname = :dirname
        """
    )
    suspend fun movieListMetaCount(profileId: Int, dirname: String): Int
}
