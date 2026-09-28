package net.reichholf.dreamdroid.data

import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.UseDrivenCache
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/** The use-driven offline cache. `ServiceRepository` takes this over with `UseDrivenCache`. */
@Singleton
class CacheRepository @Inject constructor(
    private val database: AppDatabase,
    private val profiles: ProfileRepository,
    private val connection: SessionConnectionHolder
) {
    /**
     * Drops the active profile's cache, or every profile's with [allProfiles]. A session
     * that was offline on cached data goes back to unknown.
     */
    suspend fun clearUseDrivenCache(allProfiles: Boolean) {
        if (allProfiles) {
            UseDrivenCache.clearAll(database)
        } else {
            profiles.current.value?.id?.let { UseDrivenCache.clearForProfile(database, it) }
        }
        connection.onUseDrivenCacheCleared()
    }
}
