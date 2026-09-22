package net.reichholf.dreamdroid.helpers.backup

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Sidecar of receiver profiles (including HTTP/encoder passwords) for Google cloud Auto
 * Backup. Lives under [Context.getFilesDir] so XML `domain="file"` rules can include it
 * without shipping the Room cache DB.
 */
object CloudProfilesSidecar {
    const val FILE_NAME = "cloud_profiles.json"

    private val TAG = CloudProfilesSidecar::class.java.simpleName

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun write(context: Context) {
        try {
            val profiles = runBlocking(Dispatchers.IO) {
                AppDatabase.profiles(context).getProfiles()
            }
            val data = BackupData()
            data.settings = null
            for (profile in profiles) {
                data.addProfile(profile)
            }
            file(context).writeText(GsonBuilder().create().toJson(data))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $FILE_NAME", e)
        }
    }

    /**
     * Merge profiles from a restored sidecar. Matching names are updated in place (cloud is
     * source of truth); names that only exist locally are left alone.
     */
    fun mergeFromFile(context: Context) {
        val sidecar = file(context)
        if (!sidecar.isFile) {
            return
        }
        val content = try {
            sidecar.readText()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read $FILE_NAME", e)
            return
        }
        val backupData = parseBackupImport(content)
        if (backupData == null) {
            Log.e(TAG, "Rejected unreadable $FILE_NAME")
            return
        }
        runBlocking(Dispatchers.IO) {
            val dao = AppDatabase.profiles(context)
            val existing = dao.getProfiles()
            for (profile in backupData.profiles) {
                val match = existing.firstOrNull { it.name == (profile.name ?: "") }
                if (match != null) {
                    profile.id = match.id
                    dao.updateProfile(profile)
                } else {
                    profile.id = null
                    dao.addProfile(profile)
                }
            }
        }
    }
}
