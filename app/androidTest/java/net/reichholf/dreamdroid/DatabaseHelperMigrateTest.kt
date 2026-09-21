package net.reichholf.dreamdroid

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.room.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseHelperMigrateTest {
    private lateinit var context: Context
    private lateinit var room: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME)
        room = AppDatabase.inMemory(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DatabaseHelper.DATABASE_NAME)
        room.close()
    }

    @Test
    fun missingFileDoesNotCreateDatabase() {
        assertFalse(DatabaseHelper.databaseFile(context).exists())
        assertTrue(DatabaseHelper.readProfiles(context).isEmpty())
        assertEquals(0, DatabaseHelper.migrateIntoRoomIfNeeded(context, room.profileDao()))
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
    fun migrateCopiesRowsAndDeletesLeftover() {
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

        val copied = DatabaseHelper.migrateIntoRoomIfNeeded(context, room.profileDao())
        assertEquals(1, copied)
        val roomRow = runBlocking { room.profileDao().getProfiles().single() }
        assertEquals("Box", roomRow.name)
        assertEquals("10.0.0.2", roomRow.host)
        assertEquals(443, roomRow.port)
        assertFalse(DatabaseHelper.databaseFile(context).exists())
    }

    @Test
    fun emptyLeftoverIsDeletedWhenRoomIsEmpty() {
        writeLegacy {
            execSQL("CREATE TABLE profiles (_id INTEGER PRIMARY KEY, profile TEXT)")
        }
        assertEquals(0, DatabaseHelper.migrateIntoRoomIfNeeded(context, room.profileDao()))
        assertFalse(DatabaseHelper.databaseFile(context).exists())
    }

    @Test
    fun skipsWhenRoomAlreadyHasProfiles() {
        runBlocking {
            room.profileDao().addProfile(Profile().apply { name = "RoomFirst" })
        }
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
            execSQL("INSERT INTO profiles (_id, profile, host) VALUES (1, 'Legacy', '1.2.3.4')")
        }

        assertEquals(0, DatabaseHelper.migrateIntoRoomIfNeeded(context, room.profileDao()))
        assertTrue(DatabaseHelper.databaseFile(context).exists())
        val names = runBlocking {
            room.profileDao().getProfiles().map { it.name }
        }
        assertEquals(listOf("RoomFirst"), names)
    }

    @Test
    fun corruptFileIsLeftInPlace() {
        val file = DatabaseHelper.databaseFile(context)
        file.parentFile?.mkdirs()
        file.writeText("not a sqlite database")

        assertTrue(DatabaseHelper.readProfiles(context).isEmpty())
        assertEquals(0, DatabaseHelper.migrateIntoRoomIfNeeded(context, room.profileDao()))
        assertTrue(file.exists())
    }

    private fun writeLegacy(setup: SQLiteDatabase.() -> Unit) {
        val file = DatabaseHelper.databaseFile(context)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.setup()
        }
    }
}
