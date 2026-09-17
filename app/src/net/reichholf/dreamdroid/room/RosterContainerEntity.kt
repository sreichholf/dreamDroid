package net.reichholf.dreamdroid.room

import androidx.room3.Entity

/**
 * Presence row for a roster container the user opened while we could sync.
 * Distinguishes a written-empty list from a container that was never cached.
 */
@Entity(
    tableName = "roster_container",
    primaryKeys = ["profileId", "containerRef"]
)
data class RosterContainerEntity(val profileId: Int, val containerRef: String)
