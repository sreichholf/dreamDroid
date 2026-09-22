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
        RosterContainerEntity::class,
        TimerSnapshotEntity::class,
        TimerListEntity::class,
        MovieLocationMetaEntity::class,
        MovieLocationStripEntity::class,
        MovieListMetaEntity::class,
        MovieListEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    /** Room profile DB file name under `databases/`. */
    abstract fun profileDao(): Profile.ProfileDao

    abstract fun epgDao(): EpgDao

    abstract fun rosterDao(): RosterDao

    abstract fun timerDao(): TimerDao

    abstract fun movieDao(): MovieDao

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

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `timer_snapshot` (
                        `profileId` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `timer_list` (
                        `profileId` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `reference` TEXT NOT NULL,
                        `serviceName` TEXT NOT NULL,
                        `eit` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `descriptionExtended` TEXT NOT NULL,
                        `disabled` TEXT NOT NULL,
                        `begin` TEXT NOT NULL,
                        `end` TEXT NOT NULL,
                        `duration` TEXT NOT NULL,
                        `beginReadable` TEXT NOT NULL,
                        `endReadable` TEXT NOT NULL,
                        `durationReadable` TEXT NOT NULL,
                        `startPrepare` TEXT NOT NULL,
                        `justPlay` TEXT NOT NULL,
                        `afterEvent` TEXT NOT NULL,
                        `location` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `logEntries` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `backOff` TEXT NOT NULL,
                        `nextActivation` TEXT NOT NULL,
                        `firstTryPrepare` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `repeated` TEXT NOT NULL,
                        `dontSave` TEXT NOT NULL,
                        `canceled` TEXT NOT NULL,
                        `toggleDisabled` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `position`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `movie_location_meta` (
                        `profileId` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `movie_location_strip` (
                        `profileId` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `dirname` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `position`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `movie_list_meta` (
                        `profileId` INTEGER NOT NULL,
                        `dirname` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `dirname`)
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `movie_list` (
                        `profileId` INTEGER NOT NULL,
                        `dirname` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `reference` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `descriptionExtended` TEXT NOT NULL,
                        `serviceName` TEXT NOT NULL,
                        `time` TEXT NOT NULL,
                        `timeReadable` TEXT NOT NULL,
                        `length` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileSize` TEXT NOT NULL,
                        `fileSizeReadable` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `dirname`, `position`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    ALTER TABLE `profile`
                    ADD COLUMN `zap_and_stream` INTEGER NOT NULL DEFAULT 0
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
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
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

        fun timer(context: Context): TimerDao = database(context).timerDao()

        fun movie(context: Context): MovieDao = database(context).movieDao()

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
