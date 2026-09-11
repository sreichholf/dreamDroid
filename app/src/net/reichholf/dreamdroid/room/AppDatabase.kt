package net.reichholf.dreamdroid.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.reichholf.dreamdroid.Profile

@Database(entities = [Profile::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    /** Room profile DB file name under `databases/`. */
    abstract fun profileDao(): Profile.ProfileDao

    companion object {
        const val DATABASE_NAME: String = "dreambox"

        @JvmField
        @Volatile
        var db: AppDatabase? = null

        @JvmStatic
        fun database(context: Context): AppDatabase {
            db?.let { return it }
            return synchronized(this) {
                db?.let { return it }
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { db = it }
            }
        }

        @JvmStatic
        fun profiles(context: Context): Profile.ProfileDao = database(context).profileDao()
    }
}
