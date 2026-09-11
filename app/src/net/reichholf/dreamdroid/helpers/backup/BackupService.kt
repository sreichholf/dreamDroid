package net.reichholf.dreamdroid.helpers.backup

import android.content.ContentValues
import android.content.Context
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.util.Log
import com.google.gson.GsonBuilder
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.room.AppDatabase
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Created by GAigner on 01/09/18.
 */
class BackupService(context: Context) {
    private val mContext = context
    private val mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)
    private val mProfiles: Profile.ProfileDao = AppDatabase.profiles(context)

    fun getBackupData(): BackupData {
        val export = BackupData()
        for ((key, value) in mPreferences.all) {
            export.addGenericSetting(
                GenericSetting(key, value!!.toString(), value.javaClass.simpleName),
            )
        }
        for (profile in mProfiles.getProfiles()) {
            export.addProfile(profile)
        }
        return export
    }

    fun doExport(data: BackupData?) {
        val gson = GsonBuilder().create()
        val jsonContent = gson.toJson(data)
        try {
            val filename = "dreamdroid_backup.json"
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Files.FileColumns.DISPLAY_NAME, filename)
            contentValues.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
            contentValues.put(MediaStore.Files.FileColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
            contentValues.put(MediaStore.Files.FileColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, true)
            val fileUri = mContext.contentResolver.insert(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                contentValues,
            )

            val os = mContext.contentResolver.openOutputStream(fileUri!!, "w")
            os!!.write(jsonContent.toByteArray())
            os.close()

            contentValues.clear()
            contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
            mContext.contentResolver.update(fileUri, contentValues, null, null)
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "Export unable to create export file to write the backup to.", e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun doImport(content: String?) {
        Log.i(TAG, "Import content: $content")
        val gson = GsonBuilder().create()
        val backupData = gson.fromJson(content, BackupData::class.java)

        val profiles = backupData.getProfiles()
        for (profile in profiles) {
            val existingProfile = getProfileFromDB(profile.name ?: "")
            if (existingProfile != null) {
                mProfiles.deleteProfile(existingProfile)
            }
            profile.setId(mProfiles.addProfile(profile))
        }
        val settings = backupData.getSettings()
        @Suppress("UNCHECKED_CAST")
        val allPreferences = mPreferences.all as MutableMap<String, Any>

        if (settings != null) {
            for (setting in settings) {
                allPreferences[setting.getKey()] = setting.getValue()
            }
        }
    }

    private fun getProfileFromDB(profileName: String): Profile? {
        for (profile in mProfiles.getProfiles()) {
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
