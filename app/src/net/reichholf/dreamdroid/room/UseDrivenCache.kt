package net.reichholf.dreamdroid.room

/**
 * Use-driven offline cache (tab strip, roster, EPG, timers, movies).
 *
 * Every table is keyed by profile id. Writes and reads always pass the active
 * profile; [clearForProfile] drops that profile only.
 */
object UseDrivenCache {
    suspend fun clearForProfile(db: AppDatabase, profileId: Int) {
        db.rosterDao().deleteAllForProfile(profileId)
        db.epgDao().deleteAllForProfile(profileId)
        db.timerDao().deleteAllForProfile(profileId)
        db.movieDao().deleteAllForProfile(profileId)
    }
}
