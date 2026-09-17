package net.reichholf.dreamdroid.room

import net.reichholf.dreamdroid.enigma.Timer

/**
 * Use-driven `/web/timerlist` snapshot. The hub Timer tab writes on HTTP success.
 * [load] is the shared read MultiEPG clocks can use later — one store, not a
 * second timer cache.
 */
object TimerSnapshotStore {
    suspend fun replace(dao: TimerDao, profileId: Int, timers: List<Timer>) {
        dao.replaceSnapshot(
            profileId,
            timers.mapIndexed { index, timer ->
                timer.toListEntity(profileId, index)
            }
        )
    }

    /**
     * Cached timerlist for [profileId], or null if this profile was never written.
     * Empty list means we did write and `/web/timerlist` had no rows.
     */
    suspend fun load(dao: TimerDao, profileId: Int): List<Timer>? {
        if (dao.snapshotCount(profileId) == 0) {
            return null
        }
        return dao.getTimerList(profileId).map { it.toTimer() }
    }
}
