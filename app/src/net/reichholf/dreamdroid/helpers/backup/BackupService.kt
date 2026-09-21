package net.reichholf.dreamdroid.helpers.backup

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.provider.MediaStore
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import java.io.FileNotFoundException
import java.io.IOException
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

    fun doExport(data: BackupData?): Boolean {
        val gson = GsonBuilder().create()
        val jsonContent = gson.toJson(data)
        try {
            val filename = "dreamdroid_backup.json"
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename)
            contentValues.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
            contentValues.put(
                MediaStore.Files.FileColumns.DATE_ADDED,
                System.currentTimeMillis() / 1000
            )
            contentValues.put(
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                System.currentTimeMillis() / 1000
            )
            contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, true)
            val fileUri = context.contentResolver.insert(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                contentValues
            ) ?: return false

            val os = context.contentResolver.openOutputStream(fileUri, "w") ?: return false
            os.write(jsonContent.toByteArray())
            os.close()

            contentValues.clear()
            contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
            context.contentResolver.update(fileUri, contentValues, null, null)
            return true
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Export unable to create export file to write the backup to.", e)
            return false
        } catch (e: IOException) {
            Log.e(TAG, "Export write failed.", e)
            return false
        }
    }

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
            if (existingProfile != null) {
                profileDao.deleteProfile(existingProfile)
            }
            profile.id = null
            profile.id = profileDao.addProfile(profile).toInt()
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
