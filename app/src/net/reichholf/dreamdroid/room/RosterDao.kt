package net.reichholf.dreamdroid.room

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
interface RosterDao {
    @Query(
        """
        DELETE FROM bouquet_tab
        WHERE profileId = :profileId AND kind = :kind
        """
    )
    suspend fun deleteTabStrip(profileId: Int, kind: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabStrip(rows: List<BouquetTabEntity>)

    @Transaction
    suspend fun replaceTabStrip(profileId: Int, kind: String, rows: List<BouquetTabEntity>) {
        deleteTabStrip(profileId, kind)
        if (rows.isNotEmpty()) {
            insertTabStrip(rows)
        }
    }

    @Query(
        """
        SELECT * FROM bouquet_tab
        WHERE profileId = :profileId AND kind = :kind
        ORDER BY position ASC
        """
    )
    suspend fun getTabStrip(profileId: Int, kind: String): List<BouquetTabEntity>

    @Query(
        """
        SELECT serviceRef FROM bouquet_tab
        WHERE profileId = :profileId
        """
    )
    suspend fun getTabStripRefs(profileId: Int): List<String>

    @Query(
        """
        DELETE FROM service_roster
        WHERE profileId = :profileId AND containerRef = :containerRef
        """
    )
    suspend fun deleteRosterRows(profileId: Int, containerRef: String)

    @Query(
        """
        DELETE FROM roster_container
        WHERE profileId = :profileId AND containerRef = :containerRef
        """
    )
    suspend fun deleteRosterContainer(profileId: Int, containerRef: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRosterRows(rows: List<ServiceRosterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRosterContainer(meta: RosterContainerEntity)

    @Transaction
    suspend fun replaceRoster(
        profileId: Int,
        containerRef: String,
        rows: List<ServiceRosterEntity>
    ) {
        deleteRosterRows(profileId, containerRef)
        deleteRosterContainer(profileId, containerRef)
        insertRosterContainer(RosterContainerEntity(profileId, containerRef))
        if (rows.isNotEmpty()) {
            insertRosterRows(rows)
        }
    }

    @Query(
        """
        SELECT * FROM service_roster
        WHERE profileId = :profileId AND containerRef = :containerRef
        ORDER BY position ASC
        """
    )
    suspend fun getRoster(profileId: Int, containerRef: String): List<ServiceRosterEntity>

    @Query(
        """
        SELECT COUNT(*) FROM roster_container
        WHERE profileId = :profileId AND containerRef = :containerRef
        """
    )
    suspend fun rosterContainerCount(profileId: Int, containerRef: String): Int

    @Query("DELETE FROM bouquet_tab WHERE profileId = :profileId")
    suspend fun deleteTabStripForProfile(profileId: Int)

    @Query("DELETE FROM service_roster WHERE profileId = :profileId")
    suspend fun deleteRosterRowsForProfile(profileId: Int)

    @Query("DELETE FROM roster_container WHERE profileId = :profileId")
    suspend fun deleteRosterContainersForProfile(profileId: Int)

    @Transaction
    suspend fun deleteAllForProfile(profileId: Int) {
        deleteTabStripForProfile(profileId)
        deleteRosterRowsForProfile(profileId)
        deleteRosterContainersForProfile(profileId)
    }

    @Query("DELETE FROM bouquet_tab")
    suspend fun deleteAllTabStrips()

    @Query("DELETE FROM service_roster")
    suspend fun deleteAllRosterRows()

    @Query("DELETE FROM roster_container")
    suspend fun deleteAllRosterContainers()

    @Transaction
    suspend fun deleteAll() {
        deleteAllTabStrips()
        deleteAllRosterRows()
        deleteAllRosterContainers()
    }
}
