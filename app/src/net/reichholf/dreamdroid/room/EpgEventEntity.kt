package net.reichholf.dreamdroid.room

import androidx.room.Entity

/**
 * Cached EPG event for MultiEPG (Room). Keyed per profile + service + event id.
 * Do not write through legacy [net.reichholf.dreamdroid.DatabaseHelper] `events`.
 */
@Entity(
    tableName = "epg_event",
    primaryKeys = ["profileId", "serviceRef", "eventId"],
)
data class EpgEventEntity(
    val profileId: Int,
    val serviceRef: String,
    val eventId: String,
    val start: Long,
    val duration: Long,
    val title: String,
    val description: String,
    val descriptionExtended: String,
    val serviceName: String,
    val currentTime: Long = 0L,
)
