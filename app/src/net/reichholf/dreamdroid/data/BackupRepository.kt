package net.reichholf.dreamdroid.data

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import javax.inject.Inject
import javax.inject.Singleton
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.backup.BackupData
import net.reichholf.dreamdroid.helpers.backup.GenericSetting

/**
 * Backup files: every stored preference and the saved profiles, as Gson JSON. Profile
 * writes go through [ProfileRepository], preference writes through [SettingsRepository].
 * Blocking; call off the main thread.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository
) {
    fun backupData(): BackupData {
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
     * [includePasswords] is false; [data] and its profiles are left unchanged.
     */
    fun exportJson(data: BackupData, includePasswords: Boolean): String =
        GsonBuilder().create().toJson(backupCopyForExport(data, includePasswords))

    /**
     * Imports [content]. A profile whose name is already saved replaces that row in
     * place, so it keeps its id and, when it is the active one, stays active.
     *
     * @return false when [content] cannot be imported. Nothing is changed.
     */
    fun importBackup(content: String?): Boolean {
        Log.i(TAG, "Import started")
        // Reject the whole document before writing profiles or preferences.
        val backupData = parseBackupImport(content)
        if (backupData == null) {
            Log.e(TAG, "Import rejected an unreadable backup document")
            return false
        }

        for (profile in backupData.profiles) {
            val name = profile.name ?: ""
            val existing = profiles.profiles().firstOrNull { it.name == name }
            val row = profileToInsert(profile, existing, backupData.passwordsIncluded)
            row.id = existing?.id
            profiles.save(row)
        }
        backupData.settings?.let { imported ->
            settings.restore(imported.associate { it.key to typedValue(it) })
        }
        return true
    }

    private companion object {
        const val TAG = "BackupRepository"
    }
}

/**
 * Profile row to insert.
 *
 * A null [passwordsIncluded] is a legacy file and counts as included, so [incoming] passwords
 * replace any saved ones. False keeps [existing] receiver passwords, or stores empty passwords
 * when the name is new. True also uses the incoming passwords.
 */
internal fun profileToInsert(
    incoming: Profile,
    existing: Profile?,
    passwordsIncluded: Boolean?
): Profile {
    if (passwordsIncluded != false) {
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
 * function so [BackupRepository.importBackup] can refuse the document before it writes.
 */
internal fun parseBackupImport(content: String?): BackupData? {
    val backupData = try {
        GsonBuilder().create().fromJson(content, BackupData::class.java)
    } catch (e: JsonParseException) {
        null
    } ?: return null
    if (!settingsAreImportable(backupData.settings)) {
        return null
    }
    return backupData
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
