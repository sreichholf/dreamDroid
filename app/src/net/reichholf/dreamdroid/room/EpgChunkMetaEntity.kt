package net.reichholf.dreamdroid.room

import androidx.room.Entity

/**
 * Freshness metadata for one MultiEPG bouquet time chunk (typically 24 h).
 */
@Entity(
    tableName = "epg_chunk",
    primaryKeys = ["profileId", "bouquetRef", "windowStart"],
)
data class EpgChunkMetaEntity(
    val profileId: Int,
    val bouquetRef: String,
    val windowStart: Long,
    val windowEnd: Long,
    val fetchedAtMs: Long,
)
