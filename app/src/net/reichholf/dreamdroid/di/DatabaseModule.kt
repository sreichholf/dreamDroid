package net.reichholf.dreamdroid.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.PiconSeed

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun appDatabase(
        @ApplicationContext context: Context,
        preferences: SharedPreferences
    ): AppDatabase = AppDatabase.build(context, PiconSeed.from(preferences))
}
