package net.reichholf.dreamdroid.multiepg

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.data.EpgRepository

/**
 * Transitional lookup of the process's [MultiEpgSync], which the Hilt-owned [EpgRepository]
 * builds and owns. The TV hub browse load is the last caller; delete this when it moves to
 * injected repositories (hilt-migration PR 12). Tests construct [MultiEpgSync] directly.
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
