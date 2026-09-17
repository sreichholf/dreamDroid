package net.reichholf.dreamdroid.multiepg

import android.content.Context
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Process-wide [MultiEpgSync] so the hub service list and MultiEPG share
 * in-flight `/web/epgmulti` work. Tests construct [MultiEpgSync] directly.
 */
object MultiEpgSyncHolder {
    @Volatile
    private var instance: MultiEpgSync? = null

    fun shared(context: Context): MultiEpgSync {
        instance?.let { return it }
        return synchronized(this) {
            instance?.let { return it }
            MultiEpgSync(
                dao = AppDatabase.epg(context.applicationContext),
                fetch = MultiEpgSync.httpFetch()
            ).also { instance = it }
        }
    }
}
