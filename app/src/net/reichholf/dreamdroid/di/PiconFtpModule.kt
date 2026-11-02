package net.reichholf.dreamdroid.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.reichholf.dreamdroid.helpers.CommonsNetPiconFtp
import net.reichholf.dreamdroid.helpers.PiconFtp

/**
 * On its own so instrumented tests can replace it with a fake
 * (`FakePiconFtpModule`) and drive [net.reichholf.dreamdroid.helpers.PiconSyncWorker]
 * without a receiver. Unscoped: one client per sync.
 */
@Module
@InstallIn(SingletonComponent::class)
object PiconFtpModule {
    @Provides
    fun piconFtp(): PiconFtp = CommonsNetPiconFtp()
}
