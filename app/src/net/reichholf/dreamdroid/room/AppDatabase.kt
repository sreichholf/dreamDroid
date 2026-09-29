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
    version = 9,
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

        /**
         * Adds [EpgEventEntity.titleKey] and fills it for the cached rows, so offline EPG
         * search finds them without waiting for the next MultiEPG fetch.
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    ALTER TABLE `epg_event`
                    ADD COLUMN `titleKey` TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
                val titles = ArrayList<Pair<Long, String>>()
                connection.prepare("SELECT rowid, title FROM `epg_event`").use { select ->
                    while (select.step()) {
                        titles.add(select.getLong(0) to select.getText(1))
                    }
                }
                connection.prepare(
                    "UPDATE `epg_event` SET `titleKey` = ? WHERE rowid = ?"
                ).use { update ->
                    for ((rowId, title) in titles) {
                        update.bindText(1, epgSearchKey(title))
                        update.bindLong(2, rowId)
                        update.step()
                        update.reset()
                    }
                }
            }
        }

        /**
         * The app's file-backed database. Hilt builds the one instance (DatabaseModule);
         * building does not open the file.
         */
        fun build(context: Context): AppDatabase = Room.databaseBuilder(
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
                MIGRATION_7_8,
                MIGRATION_8_9
            )
            .configureRoomDriver()
            .build()

        /** In-memory DB for instrumentation tests (does not touch the process singleton). */
        fun inMemory(context: Context): AppDatabase = Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            AppDatabase::class.java
        )
            .configureRoomDriver()
            .build()

        private fun RoomDatabase.Builder<AppDatabase>.configureRoomDriver():
            RoomDatabase.Builder<AppDatabase> =
            setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
    }
}
