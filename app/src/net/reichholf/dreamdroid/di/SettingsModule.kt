package net.reichholf.dreamdroid.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BackupDocuments
import net.reichholf.dreamdroid.data.ContentResolverBackupDocuments
import net.reichholf.dreamdroid.room.PiconSeed

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    abstract fun backupDocuments(documents: ContentResolverBackupDocuments): BackupDocuments

    companion object {
        /**
         * The default preferences, with the `R.xml.preferences` defaults written once
         * (a no-op after the first run), as the settings screen did before, and the 1.x TV
         * picon defaults that the Room migration and new profiles start from.
         */
        @Provides
        @Singleton
        fun defaultPreferences(@ApplicationContext context: Context): SharedPreferences {
            PreferenceManager.setDefaultValues(context, R.xml.preferences, false)
            return PreferenceManager.getDefaultSharedPreferences(context).also {
                PiconSeed.writeMissingTvDefaults(
                    it,
                    isTelevision = context.resources.getBoolean(R.bool.is_television)
                )
            }
        }
    }
}
