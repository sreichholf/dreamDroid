package net.reichholf.dreamdroid.helpers.backup

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.room.AppDatabase
import net.reichholf.dreamdroid.room.ProfileDaoBlocking

/**
 * Created by GAigner on 01/09/18.
 */
class BackupService(private val context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val profileDao: ProfileDaoBlocking = AppDatabase.profilesBlocking(context)

    fun getBackupData(): BackupData {
        val export = BackupData()
        for ((key, value) in preferences.all) {
            export.addGenericSetting(
                GenericSetting(key, value!!.toString(), value.javaClass.simpleName)
            )
        }
        for (profile in profileDao.getProfiles()) {
            export.addProfile(profile)
        }
        return export
    }

    /**
     * JSON for a user-chosen file. Passwords are stripped on a copy when
     * [includePasswords] is false; [data] and its profiles are left unchanged.
     */
    fun exportJson(data: BackupData, includePasswords: Boolean): String =
        GsonBuilder().create().toJson(backupCopyForExport(data, includePasswords))

    /** @return false when [content] cannot be imported. Nothing is changed. */
    fun doImport(content: String?): Boolean {
        Log.i(TAG, "Import started")
        // Reject the whole document before deleting profiles or writing preferences.
        val backupData = parseBackupImport(content)
        if (backupData == null) {
            Log.e(TAG, "Import rejected an unreadable backup document")
            return false
        }

        val profiles = backupData.profiles
        for (profile in profiles) {
            val existingProfile = getProfileFromDB(profile.name ?: "")
            val toInsert = profileToInsert(
                profile,
                existingProfile,
                backupData.passwordsIncluded
            )
            if (existingProfile != null) {
                profileDao.deleteProfile(existingProfile)
            }
            toInsert.id = null
            toInsert.id = profileDao.addProfile(toInsert).toInt()
        }
        val settings = backupData.settings ?: return true
        val editor = preferences.edit()
        for (setting in settings) {
            applyImportedSetting(editor, setting)
        }
        editor.apply()
        return true
    }

    private fun applyImportedSetting(editor: SharedPreferences.Editor, setting: GenericSetting) {
        val key = setting.key
        val value = setting.value
        when (setting.type) {
            "Boolean" -> editor.putBoolean(key, value.toBoolean())
            "Integer" -> editor.putInt(key, value.toInt())
            "Long" -> editor.putLong(key, value.toLong())
            "Float" -> editor.putFloat(key, value.toFloat())
            else -> editor.putString(key, value)
        }
    }

    private fun getProfileFromDB(profileName: String): Profile? {
        for (profile in profileDao.getProfiles()) {
            if (profile.name == profileName) {
                return profile
            }
        }
        return null
    }

    companion object {
        private val TAG = BackupService::class.java.simpleName
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
 * function so [BackupService.doImport] can refuse the document before it deletes profiles.
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
        for (setting in settings) {
            when (setting.type) {
                "Boolean" -> setting.value.toBoolean()
                "Integer" -> setting.value.toInt()
                "Long" -> setting.value.toLong()
                "Float" -> setting.value.toFloat()
                else -> setting.value
            }
        }
        true
    } catch (e: NumberFormatException) {
        false
    } catch (e: NullPointerException) {
        false
    }
}
