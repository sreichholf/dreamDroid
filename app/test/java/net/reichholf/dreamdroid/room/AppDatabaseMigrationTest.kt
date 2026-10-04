package net.reichholf.dreamdroid.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.google.gson.JsonParser
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
 * [AppDatabase.MIGRATION_8_9] fills `epg_event.titleKey` for a cached row.
 * [AppDatabase.MIGRATION_9_10] turns `encoder_stream` into `stream_mode`; the `profile`
 * table that comes out is checked against the exported schema 10.
 * [AppDatabase.MIGRATION_10_11] adds VPS to `timer_list` and `profile`; both tables are checked
 * against the exported schema 11.
 * [AppDatabase.MIGRATION_11_12] adds `profile.stream_ssl`, off for existing profiles; the
 * `profile` table is checked against the exported schema 12.
 * Raw [androidx.room3.migration.Migration.migrate] calls do not bump
 * `user_version`. Room does that when it opens the file.
 */
class AppDatabaseMigrationTest {
    @Test
    fun migratesV1ProfileThroughVersion12() {
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
                    connection.execSQL(V8_EPG_EVENT_ROW)
                    AppDatabase.MIGRATION_8_9.migrate(connection)
                    connection.execSQL(V9_ENCODER_PROFILE_ROW)
                    connection.execSQL(V9_TRUTHY_ENCODER_PROFILE_ROW)
                    AppDatabase.MIGRATION_9_10.migrate(connection)
                }
                assertTableMatchesSchema(connection, "profile", SCHEMA_10)
                connection.execSQL(V10_TIMER_LIST_ROW)
                runBlocking { AppDatabase.MIGRATION_10_11.migrate(connection) }
                assertProfileSurvived(connection)
                assertMigratedTablesExist(connection)
                assertTitleKeyBackfilled(connection)
                assertStreamModeFromEncoderFlag(connection)
                assertVpsDefaultsOff(connection)
                assertTableMatchesSchema(connection, "profile", SCHEMA_11)
                assertTableMatchesSchema(connection, "timer_list", SCHEMA_11)
                runBlocking { AppDatabase.MIGRATION_11_12.migrate(connection) }
                assertStreamSslOff(connection)
                assertTableMatchesSchema(connection, "profile", SCHEMA_12)
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

    private fun assertStreamModeFromEncoderFlag(connection: SQLiteConnection) {
        val sql =
            "SELECT _id, stream_mode, transcode_port, encoder_port, encoder_path FROM profile " +
                "ORDER BY _id"
        val rows = mutableListOf<List<Any?>>()
        connection.prepare(sql).use { statement ->
            while (statement.step()) {
                rows += listOf(
                    statement.getLong(0),
                    statement.getText(1),
                    statement.getLong(2),
                    statement.getLong(3),
                    if (statement.isNull(4)) null else statement.getText(4)
                )
            }
        }
        assertEquals(
            listOf(
                listOf(7L, "Direct", 8002L, 554L, null),
                listOf(8L, "Encoder", 8002L, 554L, null),
                listOf(9L, "Encoder", 8002L, 8554L, "live")
            ),
            rows
        )
    }

    /** Existing profiles get VPS default "No"; an existing snapshot row has unknown VPS. */
    private fun assertVpsDefaultsOff(connection: SQLiteConnection) {
        connection.prepare("SELECT DISTINCT vps_default FROM profile").use { statement ->
            assertTrue(statement.step())
            assertEquals("Off", statement.getText(0))
            assertFalse(statement.step())
        }
        connection.prepare("SELECT vpsMode, vpsTime FROM timer_list").use { statement ->
            assertTrue(statement.step())
            assertTrue(statement.isNull(0))
            assertTrue(statement.isNull(1))
            assertFalse(statement.step())
        }
    }

    /** Existing profiles keep requesting their streams over http. */
    private fun assertStreamSslOff(connection: SQLiteConnection) {
        connection.prepare("SELECT DISTINCT stream_ssl FROM profile").use { statement ->
            assertTrue(statement.step())
            assertEquals(0L, statement.getLong(0))
            assertFalse(statement.step())
        }
    }

    /**
     * The migrated [table] has the columns of the exported [schemaPath]: the check Room makes
     * when it opens the file.
     */
    private fun assertTableMatchesSchema(
        connection: SQLiteConnection,
        table: String,
        schemaPath: String
    ) {
        val actual = mutableMapOf<String, Column>()
        connection.prepare("PRAGMA table_info(`$table`)").use { statement ->
            while (statement.step()) {
                actual[statement.getText(1)] = Column(
                    type = statement.getText(2),
                    notNull = statement.getLong(3) != 0L,
                    defaultValue = if (statement.isNull(4)) null else statement.getText(4)
                )
            }
        }
        assertEquals(schemaColumns(table, schemaPath), actual, table)
    }

    private fun schemaColumns(table: String, schemaPath: String): Map<String, Column> {
        val schema = listOf(Path.of(schemaPath), Path.of("app", schemaPath)).first(Files::exists)
        val entities = JsonParser.parseString(Files.readString(schema)).asJsonObject
            .getAsJsonObject("database").getAsJsonArray("entities").map { it.asJsonObject }
        val entity = entities.single { it.get("tableName").asString == table }
        return entity.getAsJsonArray("fields").map { it.asJsonObject }.associate { field ->
            field.get("columnName").asString to Column(
                type = field.get("affinity").asString,
                notNull = field.get("notNull")?.asBoolean ?: false,
                defaultValue = field.get("defaultValue")?.asString
            )
        }
    }

    private data class Column(val type: String, val notNull: Boolean, val defaultValue: String?)

    private fun assertTitleKeyBackfilled(connection: SQLiteConnection) {
        connection.prepare("SELECT titleKey FROM epg_event").use { statement ->
            assertTrue(statement.step())
            assertEquals("die strasse", statement.getText(0))
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
            "movie_list",
            "epg_search_recent"
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

        /** A version-9 profile with the Dreambox encoder on. */
        private val V9_ENCODER_PROFILE_ROW =
            """
            INSERT INTO `profile` (
                `_id`, `profile`, `host`,
                `login`, `ssl`, `trust_all_certs`, `streamlogin`, `file_login`,
                `encoder_login`, `encoder_stream`, `file_ssl`, `simpleremote`,
                `port`, `streamport`, `fileport`, `encoder_port`,
                `encoder_audio_bitrate`, `encoder_video_bitrate`,
                `defaultProfileOnNoWifi`
            ) VALUES (
                8, 'Bedroom', '192.168.1.21',
                0, 0, 0, 0, 0,
                0, 1, 0, 0,
                80, 8001, 80, 554,
                128, 2500,
                0
            )
            """.trimIndent()

        /** A version-9 encoder profile whose flag is a truthy value other than 1. */
        private val V9_TRUTHY_ENCODER_PROFILE_ROW =
            """
            INSERT INTO `profile` (
                `_id`, `profile`, `host`, `encoder_path`,
                `login`, `ssl`, `trust_all_certs`, `streamlogin`, `file_login`,
                `encoder_login`, `encoder_stream`, `file_ssl`, `simpleremote`,
                `port`, `streamport`, `fileport`, `encoder_port`,
                `encoder_audio_bitrate`, `encoder_video_bitrate`,
                `defaultProfileOnNoWifi`
            ) VALUES (
                9, 'Kitchen', '192.168.1.22', 'live',
                0, 0, 0, 0, 0,
                0, 2, 0, 0,
                80, 8001, 80, 8554,
                128, 2500,
                0
            )
            """.trimIndent()

        private const val SCHEMA_10 = "schemas/net.reichholf.dreamdroid.room.AppDatabase/10.json"

        private const val SCHEMA_11 = "schemas/net.reichholf.dreamdroid.room.AppDatabase/11.json"

        private const val SCHEMA_12 = "schemas/net.reichholf.dreamdroid.room.AppDatabase/12.json"

        /** A version-10 timer snapshot row, from before dreamDroid kept VPS. */
        private val V10_TIMER_LIST_ROW =
            """
            INSERT INTO `timer_list` VALUES (
                7, 0, '1:0:19:283D:3FB:1:C00000:0:0:0:', 'Das Erste HD', '1234', 'Tatort', '',
                '', '0', '1700000000', '1700005400', '5400', '', '', '', '', '0', '3',
                '/media/hdd/movie/', '', '', '', '0', '', '', '0', '0', '0', '0', '0'
            )
            """.trimIndent()

        private val V8_EPG_EVENT_ROW =
            """
            INSERT INTO `epg_event` (
                `profileId`, `bouquetRef`, `serviceRef`, `eventId`, `start`, `duration`,
                `title`, `description`, `descriptionExtended`, `serviceName`,
                `currentTime`, `bouquetPos`
            ) VALUES (
                7, 'bouquet', 'service', '1', 0, 60,
                'Die Straße', '', '', 'Das Erste HD',
                0, 0
            )
            """.trimIndent()

        private fun deleteSqliteFiles(dbFile: Path) {
            Files.deleteIfExists(dbFile)
            Files.deleteIfExists(dbFile.resolveSibling("${dbFile.fileName}-wal"))
            Files.deleteIfExists(dbFile.resolveSibling("${dbFile.fileName}-shm"))
        }
    }
}
