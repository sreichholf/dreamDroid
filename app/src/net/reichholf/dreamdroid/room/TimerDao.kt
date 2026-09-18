package net.reichholf.dreamdroid.room

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
interface TimerDao {
    @Query("DELETE FROM timer_list WHERE profileId = :profileId")
    suspend fun deleteTimerRows(profileId: Int)

    @Query("DELETE FROM timer_snapshot WHERE profileId = :profileId")
    suspend fun deleteSnapshot(profileId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimerRows(rows: List<TimerListEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(meta: TimerSnapshotEntity)

    @Transaction
    suspend fun replaceSnapshot(profileId: Int, rows: List<TimerListEntity>) {
        deleteTimerRows(profileId)
        deleteSnapshot(profileId)
        insertSnapshot(TimerSnapshotEntity(profileId))
        if (rows.isNotEmpty()) {
            insertTimerRows(rows)
        }
    }

    @Query(
        """
        SELECT * FROM timer_list
        WHERE profileId = :profileId
        ORDER BY position ASC
        """
    )
    suspend fun getTimerList(profileId: Int): List<TimerListEntity>

    @Query(
        """
        SELECT COUNT(*) FROM timer_snapshot
        WHERE profileId = :profileId
        """
    )
    suspend fun snapshotCount(profileId: Int): Int

    @Transaction
    suspend fun deleteAllForProfile(profileId: Int) {
        deleteTimerRows(profileId)
        deleteSnapshot(profileId)
    }
}
