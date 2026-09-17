package net.reichholf.dreamdroid.room

import androidx.room3.Entity

/**
 * Presence row for a profile's movie location strip.
 * Distinguishes a written-empty Movies tab from a profile that was never cached.
 */
@Entity(tableName = "movie_location_meta", primaryKeys = ["profileId"])
data class MovieLocationMetaEntity(val profileId: Int)

/**
 * One ordered movie location (dirname) for a profile. Written when the user
 * opened the Movies tab while we could HTTP locations.
 */
@Entity(
    tableName = "movie_location_strip",
    primaryKeys = ["profileId", "position"]
)
data class MovieLocationStripEntity(val profileId: Int, val position: Int, val dirname: String)
