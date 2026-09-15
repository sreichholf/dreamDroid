package net.reichholf.dreamdroid.room

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

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

    @Query("DELETE FROM epg_event WHERE profileId = :profileId")
    suspend fun deleteEventsForProfile(profileId: Int)

    @Query("DELETE FROM epg_chunk WHERE profileId = :profileId")
    suspend fun deleteChunksForProfile(profileId: Int)
}
