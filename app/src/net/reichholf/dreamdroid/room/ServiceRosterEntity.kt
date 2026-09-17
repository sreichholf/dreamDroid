package net.reichholf.dreamdroid.room

import androidx.room3.Entity

/**
 * One ordered row in a cacheable user-bouquet tab or opened nested folder.
 * Kind is channel / directory / marker only — no EPG payload.
 */
@Entity(
    tableName = "service_roster",
    primaryKeys = ["profileId", "containerRef", "position"]
)
data class ServiceRosterEntity(
    val profileId: Int,
    val containerRef: String,
    val position: Int,
    val serviceRef: String,
    val name: String,
    val kind: String
)
