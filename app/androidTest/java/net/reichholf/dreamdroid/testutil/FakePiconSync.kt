package net.reichholf.dreamdroid.testutil

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.di.PiconSyncModule
import net.reichholf.dreamdroid.helpers.PiconSyncScheduler

/**
 * Stands in for WorkManager in instrumented tests. The first [enqueue] starts a sync that
 * never finishes, so every later call answers "still running". No worker runs, so there
 * is no FTP connection and no progress notification.
 */
@Singleton
class FakePiconSync @Inject constructor() : PiconSyncScheduler {
    private val calls = AtomicInteger()

    val enqueueCalls: Int
        get() = calls.get()

    override suspend fun enqueue(): Boolean = calls.incrementAndGet() == 1
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [PiconSyncModule::class])
abstract class FakePiconSyncModule {
    @Binds
    abstract fun piconSyncScheduler(fake: FakePiconSync): PiconSyncScheduler
}
