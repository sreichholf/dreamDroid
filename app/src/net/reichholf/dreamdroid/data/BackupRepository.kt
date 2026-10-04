package net.reichholf.dreamdroid.data

import android.util.Log
import androidx.sqlite.SQLiteException
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.StreamMode
import net.reichholf.dreamdroid.enigma.VpsMode
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.GenericSetting

/**
 * Backup files: every stored preference and the saved profiles, as Gson JSON. Profile
 * writes go through [ProfileRepository], preference writes through [SettingsRepository].
 * Main-safe.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository
) {
    suspend fun backupData(): BackupData {
        val export = BackupData()
        for ((key, value) in settings.all()) {
            if (value != null) {
                export.addGenericSetting(
                    GenericSetting(key, value.toString(), value.javaClass.simpleName)
                )
            }
        }
        profiles.profiles().forEach(export::addProfile)
        return export
    }

    /**
     * JSON for a user-chosen file. Passwords are stripped on a copy when
     * [includePasswords] is false; [data] and its profiles are left unchanged. Each profile
     * also carries the `encoderStream` flag of versions before [StreamMode], so they import
     * an encoder profile as one.
     */
    fun exportJson(data: BackupData, includePasswords: Boolean): String {
        val gson = GsonBuilder().create()
        val tree = gson.toJsonTree(backupCopyForExport(data, includePasswords))
        writeLegacyEncoderStream(tree)
        return gson.toJson(tree)
    }

    /** The backup in [content], or null when it cannot be imported in full. */
    suspend fun parse(content: String?): BackupData? = withContext(Dispatchers.Default) {
        parseBackupImport(content).also {
            if (it == null) {
                Log.e(TAG, "Rejected an unreadable backup document")
            }
        }
    }

    /**
     * Imports the parts of [backup] that [choice] names. A profile whose name is already
     * saved replaces that row in place, so it keeps its id and, when it is the active one,
     * stays active.
     *
     * The profiles are written in one transaction, which finishes even when the caller is
     * cancelled. The file's active profile id names a row of the device that wrote it, so it
     * is not copied with the settings: when the settings come along and that profile is
     * imported, its row here becomes the active one.
     *
     * @return false when the profiles could not be written. Nothing is changed then.
     */
    suspend fun importBackup(backup: BackupData, choice: ImportChoice): Boolean =
        withContext(Dispatchers.IO + NonCancellable) {
            Log.i(TAG, "Import started")
            val passwordsIncluded = if (choice.passwords) backup.passwordsIncluded else false
            val activeInFile = backup.settings
                ?.firstOrNull { it.key == DreamDroid.CURRENT_PROFILE }
                ?.value
                ?.toIntOrNull()
            val chosen = backup.profiles.filterIndexed { index, _ -> index in choice.profiles }
            val activeRow = chosen.indexOfFirst { activeInFile != null && it.id == activeInFile }
            val rows = try {
                val saved = profiles.profiles()
                chosen.map { profile ->
                    val existing = saved.firstOrNull { it.name == (profile.name ?: "") }
                    profileToInsert(profile, existing, passwordsIncluded)
                        .apply { id = existing?.id }
                }.also { profiles.saveAll(it) }
            } catch (e: SQLiteException) {
                Log.e(TAG, "Import failed, no profile was written", e)
                return@withContext false
            }
            if (choice.settings) {
                backup.settings?.let { imported ->
                    settings.restore(
                        imported
                            .filter { it.key != DreamDroid.CURRENT_PROFILE }
                            .associate { it.key to typedValue(it) }
                    )
                }
                rows.getOrNull(activeRow)?.id?.let { id ->
                    try {
                        profiles.setCurrent(id)
                    } catch (e: SQLiteException) {
                        Log.e(TAG, "Imported, but could not activate profile $id", e)
                    }
                }
            }
            true
        }

    private companion object {
        const val TAG = "BackupRepository"
    }
}

/**
 * What to take from a backup: the profiles at these indices of [BackupData.profiles], and
 * whether their passwords and the app settings come along.
 */
data class ImportChoice(val profiles: Set<Int>, val passwords: Boolean, val settings: Boolean)

/** False for a file exported without passwords, or one whose profiles have none. */
val BackupData.carriesPasswords: Boolean
    get() = passwordsIncluded != false &&
        profiles.any { !it.pass.isNullOrEmpty() || !it.encoderPass.isNullOrEmpty() }

/**
 * Profile row to insert.
 *
 * A null [passwordsIncluded] is a legacy file and counts as included, so [incoming] passwords
 * replace the saved ones; an empty incoming password keeps the [existing] one. False keeps
 * [existing] receiver passwords, or stores empty passwords when the name is new.
 */
internal fun profileToInsert(
    incoming: Profile,
    existing: Profile?,
    passwordsIncluded: Boolean?
): Profile {
    if (passwordsIncluded != false) {
        if (existing != null) {
            if (incoming.pass.isNullOrEmpty()) {
                incoming.pass = existing.pass
            }
            if (incoming.encoderPass.isNullOrEmpty()) {
                incoming.encoderPass = existing.encoderPass
            }
        }
        return incoming
    }
    if (existing != null) {
        incoming.pass = existing.pass
        incoming.encoderPass = existing.encoderPass
    } else {
        incoming.pass = ""
        incoming.encoderPass = ""
    }
    return incoming
}

/**
 * Copy of [source] for the export file. Profile objects are cloned before passwords are cleared
 * so the in-memory backup is not mutated.
 */
internal fun backupCopyForExport(source: BackupData, includePasswords: Boolean): BackupData {
    val gson = GsonBuilder().create()
    val copy = BackupData()
    copy.settings = source.settings
        ?.map { GenericSetting(it.key, it.value, it.type) }
        ?.toMutableList()
    copy.uri = source.uri
    copy.passwordsIncluded = includePasswords
    for (profile in source.profiles) {
        val cloned = gson.fromJson(gson.toJson(profile), Profile::class.java)
        if (!includePasswords) {
            cloned.pass = ""
            cloned.encoderPass = ""
        }
        copy.addProfile(cloned)
    }
    return copy
}

/**
 * Returns a backup that can be applied in full, or null when [content] is missing, malformed,
 * JSON null, or has a setting value that does not match its type.
 *
 * Gson parse failures ([JsonParseException], including syntax and IO errors) stay inside this
 * function, so a document is refused before [BackupRepository.importBackup] writes.
 */
internal fun parseBackupImport(content: String?): BackupData? {
    val backupData = try {
        val tree = JsonParser.parseString(content.orEmpty())
        upgradeStreamMode(tree)
        upgradeVpsDefault(tree)
        GsonBuilder().create().fromJson(tree, BackupData::class.java)
    } catch (e: JsonParseException) {
        null
    } ?: return null
    if (!settingsAreImportable(backupData.settings)) {
        return null
    }
    return backupData
}

/**
 * Files written before [StreamMode] carry the `encoderStream` flag. An unknown or missing
 * `streamMode` would make Gson store null in a non-null field, so it becomes [StreamMode.Direct].
 */
private fun upgradeStreamMode(tree: JsonElement) {
    val profiles = (tree as? JsonObject)?.get("mProfiles") as? JsonArray ?: return
    for (profile in profiles.filterIsInstance<JsonObject>()) {
        val mode = profile.get("streamMode")?.takeIf { it.isJsonPrimitive }?.asString
        if (StreamMode.entries.any { it.name == mode }) {
            continue
        }
        val encoder = profile.get("encoderStream")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean == true
        profile.addProperty(
            "streamMode",
            if (encoder) StreamMode.Encoder.name else StreamMode.Direct.name
        )
    }
}

/**
 * Files written before the VPS default have no `vpsDefault`; one from a newer app may name a
 * mode this one lacks. Either would make Gson store null in a non-null field, so it becomes
 * [VpsMode.Off].
 */
private fun upgradeVpsDefault(tree: JsonElement) {
    val profiles = (tree as? JsonObject)?.get("mProfiles") as? JsonArray ?: return
    for (profile in profiles.filterIsInstance<JsonObject>()) {
        val mode = profile.get("vpsDefault")?.takeIf { it.isJsonPrimitive }?.asString
        if (VpsMode.entries.none { it.name == mode }) {
            profile.addProperty("vpsDefault", VpsMode.Off.name)
        }
    }
}

/** `encoderStream` next to `streamMode`: true exactly for [StreamMode.Encoder]. */
private fun writeLegacyEncoderStream(tree: JsonElement) {
    val profiles = (tree as? JsonObject)?.get("mProfiles") as? JsonArray ?: return
    for (profile in profiles.filterIsInstance<JsonObject>()) {
        val mode = profile.get("streamMode")?.takeIf { it.isJsonPrimitive }?.asString
        profile.addProperty("encoderStream", mode == StreamMode.Encoder.name)
    }
}

private fun settingsAreImportable(settings: MutableList<GenericSetting>?): Boolean {
    if (settings == null) {
        return true
    }
    return try {
        settings.forEach(::typedValue)
        true
    } catch (e: NumberFormatException) {
        false
    } catch (e: NullPointerException) {
        false
    }
}

/** The preference value [setting] stores; unknown types are strings. */
private fun typedValue(setting: GenericSetting): Any? = when (setting.type) {
    "Boolean" -> setting.value.toBoolean()
    "Integer" -> setting.value.toInt()
    "Long" -> setting.value.toLong()
    "Float" -> setting.value.toFloat()
    else -> setting.value
}
