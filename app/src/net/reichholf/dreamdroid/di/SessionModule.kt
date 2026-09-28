package net.reichholf.dreamdroid.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    /**
     * Transitional: returns the static instance so Hilt and `SessionConnectionHolder.shared`
     * share one object. Delete this with the last `SessionConnectionHolder.shared` caller,
     * when `SessionConnectionHolder` gets an `@Inject` constructor (docs/hilt-migration.md).
     */
    @Provides
    @Singleton
    fun sessionConnectionHolder(): SessionConnectionHolder = SessionConnectionHolder.shared
}
