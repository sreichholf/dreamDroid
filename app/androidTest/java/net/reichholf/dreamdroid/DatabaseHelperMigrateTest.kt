package net.reichholf.dreamdroid

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.data.LegacyImportResult
import net.reichholf.dreamdroid.data.LegacyProfileImport
import net.reichholf.dreamdroid.data.LegacyProfileReadException
import net.reichholf.dreamdroid.data.RoomProfileStore
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.PiconSeed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseHelperMigrateTest {
    private lateinit var context: Context
    private lateinit var room: AppDatabase
    private lateinit var store: RoomProfileStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME)
        room = AppDatabase.inMemory(context)
        store = RoomProfileStore(
            room,
            PreferenceManager.getDefaultSharedPreferences(context)
        ) {
            error("The legacy import does not delete profiles")
        }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME)
        room.close()
    }

    @Test
    fun missingFileDoesNotCreateDatabase() = runBlocking<Unit> {
        assertFalse(DatabaseHelper.databaseFile(context).exists())
        assertTrue(DatabaseHelper.readProfiles(context).isEmpty())
        val result = migrate()
        assertEquals(0, result.imported)
        assertTrue(result.complete)
        assertFalse(DatabaseHelper.databaseFile(context).exists())
    }

    @Test
    fun readsThinPreAlterSchemaWithDefaults() {
        writeLegacy {
            execSQL(
                """
                CREATE TABLE profiles (
                    _id INTEGER PRIMARY KEY,
                    profile TEXT,
                    host TEXT,
                    port INTEGER,
                    login BOOLEAN,
                    user TEXT,
                    pass TEXT,
                    ssl BOOLEAN
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO profiles
                    (_id, profile, host, port, login, user, pass, ssl)
                VALUES (7, 'Kitchen', '192.168.0.8', 80, 1, 'root', 'secret', 0)
                """.trimIndent()
            )
        }

        val profile = DatabaseHelper.readProfiles(context).single()
        assertEquals(7, profile.id)
        assertEquals("Kitchen", profile.name)
        assertEquals("192.168.0.8", profile.host)
        assertEquals(80, profile.port)
        assertTrue(profile.login)
        assertEquals("root", profile.user)
        assertEquals("secret", profile.pass)
        assertFalse(profile.ssl)
        assertEquals(8001, profile.streamPort)
        assertEquals(80, profile.filePort)
        assertEquals("stream", profile.encoderPath)
        assertEquals(554, profile.encoderPort)
        assertEquals(128, profile.encoderAudioBitrate)
        assertEquals(2500, profile.encoderVideoBitrate)
        assertFalse(profile.allCertsTrusted)
    }

    @Test
    fun migrateCopiesRowsAndDeletesLeftover() = runBlocking<Unit> {
        writeLegacy {
            execSQL(
                """
                CREATE TABLE profiles (
                    _id INTEGER PRIMARY KEY,
                    profile TEXT,
                    host TEXT,
                    port INTEGER
                )
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO profiles (_id, profile, host, port) VALUES (3, 'Box', '10.0.0.2', 443)"
            )
            execSQL(
                """
                CREATE TABLE events (
                    id INTEGER PRIMARY KEY,
                    title TEXT
                )
                """.trimIndent()
            )
            execSQL("INSERT INTO events (id, title) VALUES (1, 'ignored')")
        }

        val copied = migrate(
            PiconSeed(online = true, onlineUseName = true, onlinePath = "/media/hdd/picon")
        )
        assertEquals(1, copied.imported)
        assertTrue(copied.complete)
        assertFalse(copied.retryable)
        val roomRow = runBlocking { room.profileDao().getProfiles().single() }
        assertEquals(3, roomRow.id)
        assertEquals("Box", roomRow.name)
        assertEquals("10.0.0.2", roomRow.host)
        assertEquals(443, roomRow.port)
        assertTrue(roomRow.piconsOnline)
        assertTrue(roomRow.piconsOnlineUseName)
        assertEquals("/media/hdd/picon", roomRow.piconsOnlinePath)
        assertFalse(DatabaseHelper.databaseFile(context).exists())
    }

    @Test
    fun emptyLeftoverIsDeletedWhenRoomIsEmpty() = runBlocking<Unit> {
        writeLegacy {
            execSQL("CREATE TABLE profiles (_id INTEGER PRIMARY KEY, profile TEXT)")
        }
        val result = migrate()
        assertEquals(0, result.imported)
        assertTrue(result.complete)
        assertFalse(DatabaseHelper.databaseFile(context).exists())
    }

    @Test
    fun mergesLeftoverRowsIntoExistingRoomProfiles() = runBlocking<Unit> {
        room.profileDao().addProfile(Profile().apply { name = "RoomFirst" })
        writeLegacy {
            execSQL(
                """
                CREATE TABLE profiles (
                    _id INTEGER PRIMARY KEY,
                    profile TEXT,
                    host TEXT
                )
                """.trimIndent()
            )
            execSQL("INSERT INTO profiles (_id, profile, host) VALUES (7, 'Legacy', '1.2.3.4')")
        }

        val result = migrate()
        assertEquals(1, result.imported)
        assertTrue(result.complete)
        assertFalse(result.retryable)
        assertFalse(DatabaseHelper.databaseFile(context).exists())
        val names = room.profileDao().getProfiles().map { it.name }.toSet()
        assertEquals(setOf("RoomFirst", "Legacy"), names)
    }

    @Test
    fun corruptFileIsDeletedAndSettlesTheImport() = runBlocking<Unit> {
        val file = DatabaseHelper.databaseFile(context)
        file.parentFile?.mkdirs()
        file.writeText("not a sqlite database")

        assertTrue(DatabaseHelper.readProfiles(context).isEmpty())
        val result = migrate()
        assertEquals(0, result.imported)
        assertTrue(result.complete)
        assertFalse(result.retryable)
        assertFalse(file.exists())
    }

    @Test
    fun anUnreadableFileIsKeptForTheNextStart() = runBlocking<Unit> {
        writeLegacy {
            execSQL("CREATE TABLE profiles (_id INTEGER PRIMARY KEY, profile TEXT, host TEXT)")
            execSQL("INSERT INTO profiles (_id, profile, host) VALUES (7, 'Legacy', '1.2.3.4')")
        }
        val file = DatabaseHelper.databaseFile(context)
        // A lock or an I/O error looks like this to SQLite: it cannot open the file right
        // now. That must not delete the user's profiles.
        assertTrue(file.setReadable(false, false))
        try {
            assertThrows(LegacyProfileReadException::class.java) {
                DatabaseHelper.readProfiles(context)
            }
            val result = migrate()
            assertFalse(result.complete)
            assertTrue(result.retryable)
            assertTrue(file.exists())
        } finally {
            file.setReadable(true, false)
        }
    }

    private fun migrate(
        picons: PiconSeed =
            PiconSeed(
                online = false,
                onlineUseName = false,
                onlinePath = Profile.DEFAULT_PICON_PATH
            )
    ): LegacyImportResult = runBlocking {
        LegacyProfileImport(store).run(DatabaseHelper.source(context), picons)
    }

    private fun writeLegacy(setup: SQLiteDatabase.() -> Unit) {
        val file = DatabaseHelper.databaseFile(context)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.setup()
        }
    }
}
