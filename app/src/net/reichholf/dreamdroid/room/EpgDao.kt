package net.reichholf.dreamdroid.room

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<EpgEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChunk(meta: EpgChunkMetaEntity)

    /**
     * Drop programmes that **start** inside the chunk. Events that began earlier
     * and span this window stay; overlapping DELETE was wiping those rows when
     * the next 24 h chunk was stored.
     */
    @Query(
        """
        DELETE FROM epg_event
        WHERE profileId = :profileId
          AND bouquetRef = :bouquetRef
          AND start >= :windowStart
          AND start < :windowEnd
        """
    )
    suspend fun deleteEventsStartingIn(
        profileId: Int,
        bouquetRef: String,
        windowStart: Long,
        windowEnd: Long
    )

    @Transaction
    suspend fun replaceChunk(meta: EpgChunkMetaEntity, events: List<EpgEventEntity>) {
        deleteEventsStartingIn(
            meta.profileId,
            meta.bouquetRef,
            meta.windowStart,
            meta.windowEnd
        )
        upsertEvents(events)
        upsertChunk(meta)
    }

    @Query(
        """
        SELECT * FROM epg_chunk
        WHERE profileId = :profileId
          AND bouquetRef = :bouquetRef
          AND windowStart = :windowStart
        LIMIT 1
        """
    )
    suspend fun getChunk(profileId: Int, bouquetRef: String, windowStart: Long): EpgChunkMetaEntity?

    @Query(
        """
        SELECT * FROM epg_event
        WHERE profileId = :profileId
          AND bouquetRef = :bouquetRef
          AND start < :windowEnd
          AND (start + duration) > :windowStart
        ORDER BY bouquetPos ASC, serviceRef ASC, start ASC
        """
    )
    suspend fun eventsOverlapping(
        profileId: Int,
        bouquetRef: String,
        windowStart: Long,
        windowEnd: Long
    ): List<EpgEventEntity>

    @Query(
        """
        SELECT * FROM epg_event
        WHERE profileId = :profileId
          AND serviceRef = :serviceRef
          AND (start + duration) > :fromSec
        ORDER BY start ASC
        """
    )
    suspend fun eventsForServiceFrom(
        profileId: Int,
        serviceRef: String,
        fromSec: Long
    ): List<EpgEventEntity>

    /**
     * Programmes of [serviceRef] that overlap [beginSec] to [endSec], soonest first. One row
     * per programme, even when several bouquets cached it.
     */
    @Query(
        """
        SELECT * FROM epg_event
        WHERE profileId = :profileId
          AND serviceRef = :serviceRef
          AND start < :endSec
          AND (start + duration) > :beginSec
        GROUP BY eventId
        ORDER BY start ASC
        """
    )
    suspend fun serviceEventsOverlapping(
        profileId: Int,
        serviceRef: String,
        beginSec: Long,
        endSec: Long
    ): List<EpgEventEntity>

    @Query(
        """
        SELECT COUNT(*) FROM epg_event
        WHERE profileId = :profileId AND serviceRef = :serviceRef
        """
    )
    suspend fun eventCountForService(profileId: Int, serviceRef: String): Int

    /**
     * Programmes of [profileId] still running or ahead at [fromSec] whose
     * [EpgEventEntity.titleKey] contains [key] (an [epgSearchKey]). One row per programme,
     * even when several bouquets cached it.
     */
    @Query(
        """
        SELECT * FROM epg_event
        WHERE profileId = :profileId
          AND (start + duration) > :fromSec
          AND instr(titleKey, :key) > 0
        GROUP BY serviceRef, eventId
        ORDER BY start ASC, serviceName ASC
        LIMIT :limit
        """
    )
    suspend fun searchTitles(
        profileId: Int,
        key: String,
        fromSec: Long,
        limit: Int
    ): List<EpgEventEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM epg_event WHERE profileId = :profileId)")
    suspend fun hasEvents(profileId: Int): Boolean

    /** The newest [limit] recent searches first. */
    @Query("SELECT * FROM epg_search_recent ORDER BY usedAtMs DESC LIMIT :limit")
    fun recentSearches(limit: Int): Flow<List<EpgSearchRecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentSearch(recent: EpgSearchRecentEntity)

    @Query(
        """
        DELETE FROM epg_search_recent
        WHERE key NOT IN (
            SELECT key FROM epg_search_recent ORDER BY usedAtMs DESC LIMIT :keep
        )
        """
    )
    suspend fun trimRecentSearches(keep: Int)

    /** Stores [recent] as the newest search and keeps the newest [keep] ones. */
    @Transaction
    suspend fun addRecentSearch(recent: EpgSearchRecentEntity, keep: Int) {
        upsertRecentSearch(recent)
        trimRecentSearches(keep)
    }

    @Query("DELETE FROM epg_search_recent WHERE key = :key")
    suspend fun deleteRecentSearch(key: String)

    @Query("DELETE FROM epg_event WHERE profileId = :profileId")
    suspend fun deleteEventsForProfile(profileId: Int)

    @Query("DELETE FROM epg_chunk WHERE profileId = :profileId")
    suspend fun deleteChunksForProfile(profileId: Int)

    @Transaction
    suspend fun deleteAllForProfile(profileId: Int) {
        deleteEventsForProfile(profileId)
        deleteChunksForProfile(profileId)
    }

    @Query("DELETE FROM epg_event")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM epg_chunk")
    suspend fun deleteAllChunks()

    @Transaction
    suspend fun deleteAll() {
        deleteAllEvents()
        deleteAllChunks()
    }

    /**
     * Programmes whose end (`start + duration`) is at or before [cutoffSec].
     * Spanning rows that still overlap the retention window stay.
     */
    @Query("DELETE FROM epg_event WHERE (start + duration) <= :cutoffSec")
    suspend fun deleteEventsEndedAtOrBefore(cutoffSec: Long)

    /** Chunks whose exclusive [EpgChunkMetaEntity.windowEnd] is at or before [cutoffSec]. */
    @Query("DELETE FROM epg_chunk WHERE windowEnd <= :cutoffSec")
    suspend fun deleteChunksEndedAtOrBefore(cutoffSec: Long)

    @Transaction
    suspend fun pruneOlderThan(cutoffSec: Long) {
        deleteEventsEndedAtOrBefore(cutoffSec)
        deleteChunksEndedAtOrBefore(cutoffSec)
    }
}
