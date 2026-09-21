package net.reichholf.dreamdroid.helpers

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFileFilter

/**
 * One-shot FTP picon sync. Runs as a foreground worker so a long download survives
 * the user leaving Settings (WorkManager + [setForeground]).
 */
class PiconSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val progress = SyncProgress()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(createForegroundInfo(applicationContext.getString(R.string.connecting)))
        try {
            syncPicons()
        } catch (e: Exception) {
            Log.e(TAG, "Picon sync failed", e)
            progress.error = true
            progress.errorText = e.message
        }
        publishFinished()
        if (progress.error || isStopped) {
            Result.failure()
        } else {
            Result.success()
        }
    }

    private suspend fun syncPicons() {
        val localPath = Picon.getBasepath(applicationContext)
        val remotePath = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, "/usr/share/enigma2/picon")
        Log.i(TAG, "Syncing from $remotePath to $localPath")
        val client = FTPClient()
        val profile = DreamDroid.getCurrentProfile()
        try {
            var tmpFile = File(localPath)
            if (!tmpFile.exists()) {
                tmpFile.mkdirs()
            }
            tmpFile = File("$localPath.nomedia")
            if (!tmpFile.exists()) {
                tmpFile.createNewFile()
            }

            publish(R.string.connecting)
            client.connect(profile.host)
            if (isStopped) {
                return
            }
            publish(R.string.connected)
            client.login(profile.user, profile.pass)
            if (isStopped) {
                return
            }
            publish(R.string.connected)

            client.setFileType(FTPClient.BINARY_FILE_TYPE)
            Log.i(TAG, "Changing to $remotePath")
            client.changeWorkingDirectory(remotePath)
            publish(R.string.getting_list_of_files)

            val filter = FTPFileFilter { file -> file.isFile && file.name.endsWith(".png") }
            val fileList = client.listFiles(null, filter)
            progress.totalFiles = fileList.size
            publish(R.string.checking)
            for (remoteFile in fileList) {
                if (isStopped) {
                    return
                }
                if (!remoteFile.isFile) {
                    continue
                }
                val fileName = remoteFile.name
                progress.currentFile = fileName
                publish(fileName)

                val localFile = File("$localPath$fileName")
                localFile.createNewFile()
                BufferedOutputStream(FileOutputStream(localFile)).use { outputStream ->
                    try {
                        client.retrieveFile(fileName, outputStream)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download picon with filename $fileName", e)
                    }
                }
                progress.downloadedFiles++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Picon sync failed", e)
            progress.error = true
            progress.errorText = e.message
        } finally {
            try {
                if (client.isConnected) {
                    client.disconnect()
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun publish(messageRes: Int) {
        publish(applicationContext.getString(messageRes))
    }

    private suspend fun publish(message: String) {
        setForeground(createForegroundInfo(message))
    }

    private fun publishFinished() {
        Picon.clearCache(applicationContext)
        val message = if (!progress.error) {
            applicationContext.getString(
                R.string.picon_sync_finished,
                progress.downloadedFiles
            )
        } else {
            progress.errorText?.takeIf { it.isNotEmpty() }
                ?: progress.currentFile.ifEmpty {
                    applicationContext.getString(R.string.error)
                }
        }
        // Separate id: WorkManager removes the FGS notification when the worker ends.
        NotificationManagerCompat.from(applicationContext).notify(
            PiconSync.COMPLETION_NOTIFICATION_ID,
            buildNotification(message, ongoing = false)
        )
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = buildNotification(message, ongoing = true)
        // Match the merged SystemForegroundService dataSync type (required for
        // targetSdk 34+; harmless earlier when the constant exists).
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                PiconSync.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(PiconSync.NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(message: String, ongoing: Boolean): Notification {
        val bm = BitmapFactory.decodeResource(
            applicationContext.resources,
            R.mipmap.ic_launcher
        )
        val builder = NotificationCompat.Builder(applicationContext, PiconSync.CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.sync_picons))
            .setContentText(message)
            .setLargeIcon(bm)
            .setSmallIcon(R.drawable.ic_action_refresh)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
        if (progress.totalFiles > 0 && ongoing) {
            builder.setProgress(progress.totalFiles, progress.downloadedFiles, false)
        }
        return builder.build()
    }

    private class SyncProgress {
        var error: Boolean = false
        var totalFiles: Int = 0
        var downloadedFiles: Int = 0
        var currentFile: String = ""
        var errorText: String? = null
    }

    companion object {
        private val TAG = PiconSyncWorker::class.java.simpleName
    }
}
