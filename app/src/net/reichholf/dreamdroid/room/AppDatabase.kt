package net.reichholf.dreamdroid.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.reichholf.dreamdroid.Profile

@Database(
    entities = [
        Profile::class,
        EpgEventEntity::class,
        EpgChunkMetaEntity::class,
    ],
    version = 2,
)
abstract class AppDatabase : RoomDatabase() {
    /** Room profile DB file name under `databases/`. */
    abstract fun profileDao(): Profile.ProfileDao

    abstract fun epgDao(): EpgDao

    companion object {
        const val DATABASE_NAME: String = "dreambox"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `epg_event` (
                        `profileId` INTEGER NOT NULL,
                        `serviceRef` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `start` INTEGER NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `descriptionExtended` TEXT NOT NULL,
                        `serviceName` TEXT NOT NULL,
                        `currentTime` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `serviceRef`, `eventId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `epg_chunk` (
                        `profileId` INTEGER NOT NULL,
                        `bouquetRef` TEXT NOT NULL,
                        `windowStart` INTEGER NOT NULL,
                        `windowEnd` INTEGER NOT NULL,
                        `fetchedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `bouquetRef`, `windowStart`)
                    )
                    """.trimIndent(),
                )
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()
                    .also { db = it }
            }
        }

        /** In-memory DB for instrumentation tests (does not touch the process singleton). */
        @JvmStatic
        fun inMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
        }

        @JvmStatic
        fun profiles(context: Context): Profile.ProfileDao = database(context).profileDao()

        @JvmStatic
        fun epg(context: Context): EpgDao = database(context).epgDao()
    }
}
