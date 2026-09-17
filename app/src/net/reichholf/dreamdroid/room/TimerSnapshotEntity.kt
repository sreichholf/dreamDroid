package net.reichholf.dreamdroid.room

import androidx.room3.Entity

/**
 * Presence row for a profile's `/web/timerlist` snapshot.
 * Distinguishes a written-empty timer tab from a profile that was never cached.
 */
@Entity(tableName = "timer_snapshot", primaryKeys = ["profileId"])
data class TimerSnapshotEntity(val profileId: Int)
