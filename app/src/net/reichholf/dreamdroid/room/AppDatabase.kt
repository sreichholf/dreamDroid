package net.reichholf.dreamdroid.room

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile

@Database(
    entities = [
        Profile::class,
        EpgEventEntity::class,
        EpgChunkMetaEntity::class,
        BouquetTabEntity::class,
        ServiceRosterEntity::class,
        RosterContainerEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    /** Room profile DB file name under `databases/`. */
    abstract fun profileDao(): Profile.ProfileDao

    abstract fun epgDao(): EpgDao

    abstract fun rosterDao(): RosterDao

    companion object {
        const val DATABASE_NAME: String = "dreambox"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
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
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `epg_chunk` (
                        `profileId` INTEGER NOT NULL,
                        `bouquetRef` TEXT NOT NULL,
                        `windowStart` INTEGER NOT NULL,
                        `windowEnd` INTEGER NOT NULL,
                        `fetchedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`, `bouquetRef`, `windowStart`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE `epg_event_new` (
                        `profileId` INTEGER NOT NULL,
                        `bouquetRef` TEXT NOT NULL,
                        `serviceRef` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `start` INTEGER NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `descriptionExtended` TEXT NOT NULL,
                        `serviceName` TEXT NOT NULL,
                        `currentTime` INTEGER NOT NULL,
                        PRIMARY KEY(
                            `profileId`,
                            `bouquetRef`,
                            `serviceRef`,
                            `eventId`
                        )
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO `epg_event_new` (
                        `profileId`, `bouquetRef`, `serviceRef`, `eventId`,
                        `start`, `duration`, `title`, `description`,
                        `descriptionExtended`, `serviceName`, `currentTime`
                    )
                    SELECT
                        `profileId`, '', `serviceRef`, `eventId`,
                        `start`, `duration`, `title`, `description`,
                        `descriptionExtended`, `serviceName`, `currentTime`
                    FROM `epg_event`
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE `epg_event`")
                connection.execSQL(
                    "ALTER TABLE `epg_event_new` RENAME TO `epg_event`"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    ALTER TABLE `epg_event`
                    ADD COLUMN `bouquetPos` INTEGER NOT NULL DEFAULT 0
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bouquet_tab` (
                        `profileId` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `serviceRef` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `kind`, `position`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `service_roster` (
                        `profileId` INTEGER NOT NULL,
                        `containerRef` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `serviceRef` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `containerRef`, `position`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `roster_container` (
                        `profileId` INTEGER NOT NULL,
                        `containerRef` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `containerRef`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        var db: AppDatabase? = null

        fun database(context: Context): AppDatabase {
            db?.let { return it }
            return synchronized(this) {
                db?.let { return it }
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5
                    )
                    .configureRoomDriver()
                    .build()
                    .also { db = it }
            }
        }

        /** In-memory DB for instrumentation tests (does not touch the process singleton). */
        fun inMemory(context: Context): AppDatabase = Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            AppDatabase::class.java
        )
            .configureRoomDriver()
            .build()

        fun profiles(context: Context): Profile.ProfileDao = database(context).profileDao()

        /**
         * Blocking profile DAO for Application, backup, and other main-thread callers.
         * Suspend DAOs go through [profiles] from an existing coroutine.
         */
        fun profilesBlocking(context: Context): ProfileDaoBlocking =
            ProfileDaoBlocking(database(context).profileDao())

        fun epg(context: Context): EpgDao = database(context).epgDao()

        fun roster(context: Context): RosterDao = database(context).rosterDao()

        private fun RoomDatabase.Builder<AppDatabase>.configureRoomDriver():
            RoomDatabase.Builder<AppDatabase> =
            setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
    }
}

/** Single `runBlocking(IO)` facade over [Profile.ProfileDao]. */
class ProfileDaoBlocking internal constructor(private val dao: Profile.ProfileDao) {
    fun addProfile(profile: Profile): Long = runBlocking(Dispatchers.IO) {
        dao.addProfile(profile)
    }

    fun updateProfile(profile: Profile) = runBlocking(Dispatchers.IO) {
        dao.updateProfile(profile)
    }

    fun deleteProfile(profile: Profile) = runBlocking(Dispatchers.IO) {
        dao.deleteProfile(profile)
    }

    fun getProfiles(): MutableList<Profile> = runBlocking(Dispatchers.IO) {
        dao.getProfiles()
    }

    fun getProfile(id: Int): Profile? = runBlocking(Dispatchers.IO) {
        dao.getProfile(id)
    }
}
