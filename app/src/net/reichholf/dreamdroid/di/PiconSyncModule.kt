package net.reichholf.dreamdroid.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.helpers.PiconSyncScheduler
import net.reichholf.dreamdroid.helpers.WorkManagerPiconSync

/** On its own so instrumented tests can swap in a fake with `@TestInstallIn`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PiconSyncModule {
    @Binds
    abstract fun piconSyncScheduler(sync: WorkManagerPiconSync): PiconSyncScheduler
}
