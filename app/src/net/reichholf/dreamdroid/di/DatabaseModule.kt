package net.reichholf.dreamdroid.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.reichholf.dreamdroid.room.AppDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * The instance `AppDatabase`'s static accessors return, so both share one database
     * until the statics lose their last caller. Building it does not open the file.
     */
    @Provides
    @Singleton
    fun appDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.database(context)
}
