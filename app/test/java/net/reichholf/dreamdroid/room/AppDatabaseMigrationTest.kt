package net.reichholf.dreamdroid.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Version 1 is the profile table only. [AppDatabase.MIGRATION_7_8] adds
 * `zap_and_stream`; every other profile column is already in the v1 table.
 * Raw [androidx.room3.migration.Migration.migrate] calls do not bump
 * `user_version`. Room does that when it opens the file.
 */
class AppDatabaseMigrationTest {
    @Test
    fun migratesV1ProfileThroughVersion8() {
        val dbFile = Files.createTempFile("dreambox-v1", ".db")
        Files.delete(dbFile)
        try {
            BundledSQLiteDriver().open(dbFile.toString()).use { connection ->
                connection.execSQL(V1_PROFILE)
                connection.execSQL(V1_PROFILE_ROW)
                connection.execSQL("PRAGMA user_version = 1")
                runBlocking {
                    AppDatabase.MIGRATION_1_2.migrate(connection)
                    AppDatabase.MIGRATION_2_3.migrate(connection)
                    AppDatabase.MIGRATION_3_4.migrate(connection)
                    AppDatabase.MIGRATION_4_5.migrate(connection)
                    AppDatabase.MIGRATION_5_6.migrate(connection)
                    AppDatabase.MIGRATION_6_7.migrate(connection)
                    AppDatabase.MIGRATION_7_8.migrate(connection)
                }
                assertProfileSurvived(connection)
                assertMigratedTablesExist(connection)
            }
        } finally {
            deleteSqliteFiles(dbFile)
        }
    }

    private fun assertProfileSurvived(connection: SQLiteConnection) {
        val sql =
            "SELECT profile, host, user, pass, zap_and_stream FROM profile WHERE _id = 7"
        connection.prepare(sql).use { statement ->
            assertTrue(statement.step())
            assertEquals("Living Room", statement.getText(0))
            assertEquals("192.168.1.20", statement.getText(1))
            assertEquals("root", statement.getText(2))
            assertEquals("secret", statement.getText(3))
            assertEquals(0L, statement.getLong(4))
            assertFalse(statement.step())
        }
    }

    private fun assertMigratedTablesExist(connection: SQLiteConnection) {
        val names = mutableSetOf<String>()
        connection.prepare(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        ).use { statement ->
            while (statement.step()) {
                names.add(statement.getText(0))
            }
        }
        listOf(
            "epg_event",
            "epg_chunk",
            "bouquet_tab",
            "service_roster",
            "roster_container",
            "timer_snapshot",
            "timer_list",
            "movie_location_meta",
            "movie_location_strip",
            "movie_list_meta",
            "movie_list"
        ).forEach { table ->
            assertTrue(table in names, "missing $table")
        }
    }

    private companion object {
        /**
         * Room's version-8 `profile` createSql without `zap_and_stream`.
         * Boolean columns are INTEGER. Nullable strings have no NOT NULL.
         */
        private val V1_PROFILE =
            """
            CREATE TABLE IF NOT EXISTS `profile` (
                `_id` INTEGER PRIMARY KEY AUTOINCREMENT,
                `profile` TEXT,
                `host` TEXT,
                `streamhost` TEXT,
                `encoder_path` TEXT,
                `user` TEXT,
                `pass` TEXT,
                `encoder_user` TEXT,
                `encoder_pass` TEXT,
                `login` INTEGER NOT NULL,
                `ssl` INTEGER NOT NULL,
                `trust_all_certs` INTEGER NOT NULL,
                `streamlogin` INTEGER NOT NULL,
                `file_login` INTEGER NOT NULL,
                `encoder_login` INTEGER NOT NULL,
                `encoder_stream` INTEGER NOT NULL,
                `file_ssl` INTEGER NOT NULL,
                `simpleremote` INTEGER NOT NULL,
                `port` INTEGER NOT NULL,
                `streamport` INTEGER NOT NULL,
                `fileport` INTEGER NOT NULL,
                `encoder_port` INTEGER NOT NULL,
                `encoder_audio_bitrate` INTEGER NOT NULL,
                `encoder_video_bitrate` INTEGER NOT NULL,
                `default_ref` TEXT,
                `default_ref_name` TEXT,
                `default_ref_2` TEXT,
                `default_ref_2_name` TEXT,
                `ssid` TEXT,
                `defaultProfileOnNoWifi` INTEGER NOT NULL
            )
            """.trimIndent()

        private val V1_PROFILE_ROW =
            """
            INSERT INTO `profile` (
                `_id`, `profile`, `host`, `user`, `pass`,
                `login`, `ssl`, `trust_all_certs`, `streamlogin`, `file_login`,
                `encoder_login`, `encoder_stream`, `file_ssl`, `simpleremote`,
                `port`, `streamport`, `fileport`, `encoder_port`,
                `encoder_audio_bitrate`, `encoder_video_bitrate`,
                `defaultProfileOnNoWifi`
            ) VALUES (
                7, 'Living Room', '192.168.1.20', 'root', 'secret',
                1, 0, 0, 0, 0,
                0, 0, 0, 0,
                80, 8001, 80, 554,
                128, 2500,
                0
            )
            """.trimIndent()

        private fun deleteSqliteFiles(dbFile: Path) {
            Files.deleteIfExists(dbFile)
            Files.deleteIfExists(dbFile.resolveSibling("${dbFile.fileName}-wal"))
            Files.deleteIfExists(dbFile.resolveSibling("${dbFile.fileName}-shm"))
        }
    }
}
