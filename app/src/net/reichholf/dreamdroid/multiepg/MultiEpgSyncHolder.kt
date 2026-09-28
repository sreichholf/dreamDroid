package net.reichholf.dreamdroid.multiepg

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.data.EpgRepository

/**
 * Transitional lookup of the process's [MultiEpgSync], which the Hilt-owned [EpgRepository]
 * builds and owns. The hub service list and the TV hub browse load reach it here until they
 * move to injected repositories (hilt-migration PRs 9 and 12); delete this with the last
 * caller. Tests construct [MultiEpgSync] directly.
 */
object MultiEpgSyncHolder {
    fun shared(context: Context): MultiEpgSync = EntryPointAccessors
        .fromApplication(context.applicationContext, MultiEpgSyncEntryPoint::class.java)
        .epgRepository()
        .multiEpgSync
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MultiEpgSyncEntryPoint {
    fun epgRepository(): EpgRepository
}
