package net.reichholf.dreamdroid.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.room.PiconSeed
import net.reichholf.dreamdroid.testutil.MemorySharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyProfileImportTest {
    @Test
    fun aFailedImportRollsBackAndTheRetryAddsEachRowOnce() = runBlocking<Unit> {
        val source = FakeLegacySource(listOf(profile(1, "one"), profile(2, "two")))
        val store = ImportStore().apply { failAddAt = 2 }
        val importer = LegacyProfileImport(store)

        val failed = importer.run(source, PICONS)

        assertFalse(failed.complete)
        assertTrue(failed.retryable)
        assertEquals(0, failed.imported)
        assertTrue(store.profiles().isEmpty())
        assertFalse(source.deleted)

        store.failAddAt = null
        val retried = importer.run(source, PICONS)

        assertTrue(retried.complete)
        assertFalse(retried.retryable)
        assertEquals(2, retried.imported)
        assertEquals(listOf(1, 2), store.profiles().map { it.id })
        assertTrue(source.deleted)
    }

    @Test
    fun aCompleteImportDeletesTheLeftoverAndConsumesTheLegacySettings() = runBlocking<Unit> {
        val source = FakeLegacySource(listOf(profile(1, "one"), profile(2, "two")))
        val store = ImportStore()
        val repo = ProfileRepository(store, WebIfCapabilitiesRepository())

        val result = repo.loadCurrent(source)

        assertTrue(result.complete)
        assertEquals(2, result.imported)
        assertTrue(source.deleted)
        assertTrue(store.legacySettingsConsumed)
        assertEquals(listOf(1, 2), store.profiles().map { it.id })
        assertEquals(1, repo.current.value?.id)
    }

    @Test
    fun aFailedImportKeepsTheLegacySettingsAndSkipsThePrefsProfile() = runBlocking<Unit> {
        val source = FakeLegacySource(listOf(profile(1, "legacy")))
        val store = ImportStore(prefsProfile = profile(null, "from-prefs")).apply {
            failAddAt = 1
        }
        val repo = ProfileRepository(store, WebIfCapabilitiesRepository())

        val result = repo.loadCurrent(source)

        assertTrue(result.retryable)
        assertFalse(store.legacySettingsConsumed)
        assertTrue(store.profiles().isEmpty())
        assertNull(repo.current.value)
    }

    @Test
    fun anEmptyLeftoverBuildsThePrefsProfileBeforeConsumingTheSettings() = runBlocking<Unit> {
        val source = FakeLegacySource(emptyList())
        val store = ImportStore(prefsProfile = profile(null, "from-prefs"))
        val repo = ProfileRepository(store, WebIfCapabilitiesRepository())

        val result = repo.loadCurrent(source)

        assertTrue(result.complete)
        assertEquals(0, result.imported)
        assertTrue(source.deleted)
        assertTrue(store.legacySettingsConsumed)
        assertEquals(listOf("from-prefs"), store.profiles().map { it.name })
        assertEquals("from-prefs", repo.current.value?.name)
    }

    @Test
    fun aCorruptLeftoverIsDeletedAndTheLegacySettingsAreConsumed() = runBlocking<Unit> {
        val source = FakeLegacySource(null)
        val store = ImportStore(prefsProfile = profile(null, "from-prefs"))
        val repo = ProfileRepository(store, WebIfCapabilitiesRepository())

        val result = repo.loadCurrent(source)

        assertTrue(result.complete)
        assertFalse(result.retryable)
        assertEquals(0, result.imported)
        assertTrue(source.deleted)
        assertTrue(store.legacySettingsConsumed)
        assertEquals(listOf("from-prefs"), store.profiles().map { it.name })
        assertEquals("from-prefs", repo.current.value?.name)
    }

    @Test
    fun consumingTheLegacySettingsRemovesTheReceiverKeysAndKeepsTheActiveProfile() {
        val preferences = MemorySharedPreferences()
        preferences.edit()
            .putString("host", "10.0.0.9")
            .putString("pass", "secret")
            .putInt(DreamDroid.CURRENT_PROFILE, 42)
            .apply()

        LegacyProfileSettings.consume(preferences)

        assertFalse(preferences.contains("host"))
        assertFalse(preferences.contains("pass"))
        assertEquals(42, preferences.getInt(DreamDroid.CURRENT_PROFILE, -1))
    }

    @Test
    fun aLeftoverNextToRoomProfilesImportsTheMissingRows() = runBlocking<Unit> {
        val source = FakeLegacySource(listOf(profile(5, "room"), profile(1, "legacy")))
        val store = ImportStore(rows = listOf(profile(5, "room")))
        val repo = ProfileRepository(store, WebIfCapabilitiesRepository())

        val result = repo.loadCurrent(source)

        assertTrue(result.complete)
        assertFalse(result.retryable)
        assertEquals(1, result.imported)
        assertTrue(source.deleted)
        assertTrue(store.legacySettingsConsumed)
        assertEquals(listOf(5, 1), store.profiles().map { it.id })
        assertEquals(5, repo.current.value?.id)
    }

    @Test
    fun aRowRoomHasUnderAnotherIdIsNotImportedAgain() = runBlocking<Unit> {
        val source = FakeLegacySource(listOf(profile(1, "room")))
        val store = ImportStore(rows = listOf(profile(5, "room")))

        val result = LegacyProfileImport(store).run(source, PICONS)

        assertTrue(result.complete)
        assertEquals(0, result.imported)
        assertTrue(source.deleted)
        assertEquals(listOf(5), store.profiles().map { it.id })
    }

    @Test
    fun aRetriedImportAddsNoDuplicateRows() = runBlocking<Unit> {
        val source = FakeLegacySource(
            listOf(profile(5, "room"), profile(1, "legacy")),
            deleteSucceeds = false
        )
        val store = ImportStore(rows = listOf(profile(5, "room")))
        val importer = LegacyProfileImport(store)

        val imported = importer.run(source, PICONS)

        assertTrue(imported.complete)
        assertEquals(1, imported.imported)
        assertFalse(source.deleted)

        source.deleteSucceeds = true
        val retried = importer.run(source, PICONS)

        assertTrue(retried.complete)
        assertEquals(0, retried.imported)
        assertTrue(source.deleted)
        assertEquals(listOf(5, 1), store.profiles().map { it.id })
    }

    @Test
    fun aTemporaryReadErrorKeepsTheLeftoverForTheNextStart() = runBlocking<Unit> {
        val source = FakeLegacySource(
            listOf(profile(1, "one")),
            readError = LegacyProfileReadException(IOException("database is locked"))
        )
        val store = ImportStore()
        val repo = ProfileRepository(store, WebIfCapabilitiesRepository())

        val failed = repo.loadCurrent(source)

        assertFalse(failed.complete)
        assertTrue(failed.retryable)
        assertFalse(source.deleted)
        assertFalse(store.legacySettingsConsumed)
        assertTrue(store.profiles().isEmpty())
        assertNull(repo.current.value)

        source.readError = null
        val retried = repo.loadCurrent(source)

        assertTrue(retried.complete)
        assertEquals(1, retried.imported)
        assertTrue(source.deleted)
        assertEquals(listOf(1), store.profiles().map { it.id })
    }

    @Test
    fun leftoverProfilesThatShareANameBothImport() = runBlocking<Unit> {
        val source = FakeLegacySource(
            listOf(
                profile(1, "10.0.0.1").apply { name = "shared" },
                profile(2, "10.0.0.2").apply { name = "shared" }
            )
        )
        val store = ImportStore()

        val result = LegacyProfileImport(store).run(source, PICONS)

        assertTrue(result.complete)
        assertEquals(2, result.imported)
        assertTrue(source.deleted)
        assertEquals(listOf(1, 2), store.profiles().map { it.id })
    }
}

private val PICONS = PiconSeed(
    online = true,
    onlineUseName = true,
    onlinePath = "/media/hdd/picon"
)

private fun profile(id: Int?, host: String): Profile = Profile().apply {
    this.id = id
    this.host = host
    name = host
}

private class FakeLegacySource(
    private val rows: List<Profile>?,
    var deleteSucceeds: Boolean = true,
    var readError: Exception? = null
) : LegacyProfileSource {
    var deleted = false
        private set

    override suspend fun read(): List<Profile>? {
        readError?.let { throw it }
        return rows
    }

    override suspend fun delete(): Boolean {
        if (!deleteSucceeds) {
            return false
        }
        deleted = true
        return true
    }
}

/**
 * Profiles in memory. [transaction] rolls the rows back when its block throws, as Room
 * does; [failAddAt] fails that many adds into the store's lifetime. The 1.x settings are
 * [MemorySharedPreferences] holding a receiver password, consumed like the real store's.
 */
private class ImportStore(
    rows: List<Profile> = emptyList(),
    private val prefsProfile: Profile = Profile.getDefault()
) : ProfileStore {
    private val rows = rows.toMutableList()
    private val preferences = MemorySharedPreferences().apply {
        edit().putString("pass", "legacy-secret").apply()
    }
    private var adds = 0
    private var remembered = -1

    var failAddAt: Int? = null
    val legacySettingsConsumed: Boolean
        get() = !preferences.contains("pass")

    override suspend fun profiles(): List<Profile> = rows.toList()

    override suspend fun profile(id: Int): Profile? = rows.firstOrNull { it.id == id }

    override suspend fun add(profile: Profile): Long {
        if (++adds == failAddAt) {
            throw IllegalStateException("disk full")
        }
        val id = profile.id ?: ((rows.mapNotNull { it.id }.maxOrNull() ?: 0) + 1)
        profile.id = id
        rows.removeAll { it.id == id }
        rows.add(profile)
        return id.toLong()
    }

    override suspend fun update(profile: Profile) {
        rows.replaceAll { if (it.id == profile.id) profile else it }
    }

    override suspend fun delete(profile: Profile) {
        rows.removeAll { it.id == profile.id }
    }

    override suspend fun <R> transaction(block: suspend () -> R): R {
        val snapshot = rows.toMutableList()
        try {
            return block()
        } catch (e: Throwable) {
            rows.clear()
            rows.addAll(snapshot)
            throw e
        }
    }

    override fun activeId(): Int = remembered

    override fun setActiveId(id: Int) {
        remembered = id
    }

    override fun clearActiveId() {
        remembered = -1
    }

    override fun xmlDebug(): Boolean = false

    override fun piconDefaults(): PiconSeed = PICONS

    override fun legacyProfile(): Profile = prefsProfile

    override fun consumeLegacyProfileSettings() {
        LegacyProfileSettings.consume(preferences)
    }
}
