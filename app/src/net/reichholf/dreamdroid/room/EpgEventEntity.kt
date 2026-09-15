package net.reichholf.dreamdroid.room

import androidx.room3.Entity

/**
 * Cached EPG event for MultiEPG (Room). Keyed per profile + bouquet + service
 * + event id. Do not write through legacy
 * [net.reichholf.dreamdroid.DatabaseHelper] `events`.
 */
@Entity(
    tableName = "epg_event",
    primaryKeys = ["profileId", "bouquetRef", "serviceRef", "eventId"]
)
data class EpgEventEntity(
    val profileId: Int,
    val bouquetRef: String,
    val serviceRef: String,
    val eventId: String,
    val start: Long,
    val duration: Long,
    val title: String,
    val description: String,
    val descriptionExtended: String,
    val serviceName: String,
    val currentTime: Long = 0L,
    val bouquetPos: Int = 0
)
