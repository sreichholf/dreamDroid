package net.reichholf.dreamdroid.data

import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.StreamMode
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.GenericSetting
import net.reichholf.dreamdroid.testutil.TestProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [BackupRepository] over Room in memory and the default preferences of a test context. */
class BackupRepositoryTest {
    private val testProfiles = TestProfiles()
    private val profiles = testProfiles.repository
    private val preferences = PreferenceManager.getDefaultSharedPreferences(testProfiles.context)
    private val backups = BackupRepository(profiles, SettingsRepository(preferences))

    @Test
    fun backupHoldsEveryPreferenceWithItsTypeAndTheProfiles() = runBlocking<Unit> {
        preferences.edit()
            .putBoolean(DreamDroid.PREFS_KEY_XML_DEBUG, true)
            .putString(DreamDroid.PREFS_KEY_THEME_TYPE, "0")
            .putInt(DreamDroid.CURRENT_PROFILE, 3)
            .apply()
        saved("Living Room", "10.0.0.1")

        val data = backups.backupData()

        assertEquals(
            setOf(
                Triple(DreamDroid.PREFS_KEY_XML_DEBUG, "true", "Boolean"),
                Triple(DreamDroid.PREFS_KEY_THEME_TYPE, "0", "String"),
                Triple(DreamDroid.CURRENT_PROFILE, "3", "Integer")
            ),
            data.settings.orEmpty().map { Triple(it.key, it.value, it.type) }.toSet()
        )
        assertEquals(listOf("Living Room"), data.profiles.map { it.name })
    }

    @Test
    fun importStoresSettingsWithTheirTypes() = runBlocking<Unit> {
        val data = BackupData()
        data.addGenericSetting(GenericSetting("import_probe", "from-backup", "String"))
        data.addGenericSetting(GenericSetting(DreamDroid.PREFS_KEY_INSTANT_ZAP, "true", "Boolean"))
        data.addGenericSetting(GenericSetting(DreamDroid.CURRENT_PROFILE, "7", "Integer"))

        assertTrue(backups.importBackup(json(data)))

        assertEquals("from-backup", preferences.getString("import_probe", null))
        assertEquals(true, preferences.getBoolean(DreamDroid.PREFS_KEY_INSTANT_ZAP, false))
        assertEquals(7, preferences.getInt(DreamDroid.CURRENT_PROFILE, -1))
    }

    @Test
    fun importDoesNotReplaceADifferentlyNamedProfile() = runBlocking<Unit> {
        val kitchen = saved("Kitchen", "10.0.0.1")
        val data = BackupData()
        data.addProfile(receiver("Bedroom", "9.9.9.9").apply { id = kitchen.id })

        assertTrue(backups.importBackup(json(data)))

        val byName = profiles.profiles().associate { it.name to it.host }
        assertEquals(mapOf("Kitchen" to "10.0.0.1", "Bedroom" to "9.9.9.9"), byName)
    }

    @Test
    fun importReplacesASameNamedProfileInPlace() = runBlocking<Unit> {
        val living = saved("Living Room", "10.0.0.1")
        profiles.setCurrent(living.id!!)
        val data = BackupData()
        data.addProfile(receiver("Living Room", "10.0.0.2").apply { id = 99 })

        assertTrue(backups.importBackup(json(data)))

        val rows = profiles.profiles()
        assertEquals(listOf(living.id), rows.map { it.id })
        assertEquals("10.0.0.2", rows.single().host)
        assertEquals("10.0.0.2", profiles.requireCurrent().host)
    }

    @Test
    fun importWithoutPasswordsKeepsTheSavedOnes() = runBlocking<Unit> {
        saved("Living Room", "10.0.0.1").also {
            it.pass = "secret"
            profiles.save(it)
        }
        val data = BackupData()
        data.passwordsIncluded = false
        data.addProfile(receiver("Living Room", "10.0.0.2"))

        assertTrue(backups.importBackup(json(data)))

        assertEquals("secret", profiles.profiles().single().pass)
    }

    @Test
    fun unreadableDocumentChangesNothing() = runBlocking<Unit> {
        saved("Living Room", "10.0.0.1")

        assertFalse(backups.importBackup("{"))

        assertEquals(listOf("Living Room"), profiles.profiles().map { it.name })
        assertNull(preferences.getString("import_probe", null))
    }

    @Test
    fun exportWithoutPasswordsClearsThemInTheFileOnly() = runBlocking<Unit> {
        saved("Living Room", "10.0.0.1").also {
            it.pass = "secret"
            profiles.save(it)
        }
        val data = backups.backupData()

        val exported = GsonBuilder().create()
            .fromJson(backups.exportJson(data, includePasswords = false), BackupData::class.java)

        assertEquals("", exported.profiles.single().pass)
        assertEquals(false, exported.passwordsIncluded)
        assertEquals("secret", data.profiles.single().pass)
    }

    @Test
    fun exportKeepsTheEncoderFlagOfOlderVersions() = runBlocking<Unit> {
        saved("Encoder", "10.0.0.1").also {
            it.streamMode = StreamMode.Encoder
            profiles.save(it)
        }
        saved("Transcoding", "10.0.0.2").also {
            it.streamMode = StreamMode.Transcoding
            profiles.save(it)
        }

        val exported = JsonParser.parseString(
            backups.exportJson(backups.backupData(), includePasswords = true)
        ).asJsonObject.getAsJsonArray("mProfiles").map { it.asJsonObject }

        assertEquals(
            listOf(
                Triple("Encoder", "Encoder", true),
                Triple("Transcoding", "Transcoding", false)
            ),
            exported.map {
                Triple(
                    it.get("name").asString,
                    it.get("streamMode").asString,
                    it.get("encoderStream").asBoolean
                )
            }.sortedBy { it.first }
        )
    }

    private fun saved(name: String, host: String): Profile =
        receiver(name, host).also { runBlocking { profiles.save(it) } }

    private fun receiver(name: String, host: String): Profile = Profile.getDefault().apply {
        this.name = name
        this.host = host
    }

    private fun json(data: BackupData): String = GsonBuilder().create().toJson(data)
}
