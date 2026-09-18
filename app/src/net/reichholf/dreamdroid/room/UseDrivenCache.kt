package net.reichholf.dreamdroid.room

/**
 * Use-driven offline cache (tab strip, roster, EPG, timers, movies).
 *
 * Every table is keyed by profile id. Writes and reads always pass the active
 * profile; [clearForProfile] drops that profile only, [clearAll] drops every
 * profile's cache.
 */
object UseDrivenCache {
    suspend fun clearForProfile(db: AppDatabase, profileId: Int) {
        db.rosterDao().deleteAllForProfile(profileId)
        db.epgDao().deleteAllForProfile(profileId)
        db.timerDao().deleteAllForProfile(profileId)
        db.movieDao().deleteAllForProfile(profileId)
    }

    suspend fun clearAll(db: AppDatabase) {
        db.rosterDao().deleteAll()
        db.epgDao().deleteAll()
        db.timerDao().deleteAll()
        db.movieDao().deleteAll()
    }
}
