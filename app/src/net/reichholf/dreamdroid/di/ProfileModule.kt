package net.reichholf.dreamdroid.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.reichholf.dreamdroid.data.ProfileRepository

@Module
@InstallIn(SingletonComponent::class)
object ProfileModule {
    /**
     * Transitional: returns the static instance so Hilt and `ProfileRepository.get()`
     * share one object. Delete this with the last `ProfileRepository.get()` caller,
     * when `ProfileRepository` gets an `@Inject` constructor (docs/hilt-migration.md).
     */
    @Provides
    @Singleton
    fun profileRepository(@ApplicationContext context: Context): ProfileRepository =
        ProfileRepository.install(context)
}
