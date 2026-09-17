package net.reichholf.dreamdroid.room

import androidx.room3.Entity

/**
 * One user-bouquet hub tab (Favourites, Sports, …) for a profile + TV/Radio kind.
 * Dedicated Provider / All Services tabs are not stored.
 */
@Entity(
    tableName = "bouquet_tab",
    primaryKeys = ["profileId", "kind", "position"]
)
data class BouquetTabEntity(
    val profileId: Int,
    val kind: String,
    val position: Int,
    val serviceRef: String,
    val name: String
)
