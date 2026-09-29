package net.reichholf.dreamdroid.room

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * One recent EPG search, for every profile. Keyed by [epgSearchKey] of the query, so
 * "Tatort" and "tatort" are one entry that shows the latest spelling.
 */
@Entity(tableName = "epg_search_recent")
data class EpgSearchRecentEntity(@PrimaryKey val key: String, val query: String, val usedAtMs: Long)
