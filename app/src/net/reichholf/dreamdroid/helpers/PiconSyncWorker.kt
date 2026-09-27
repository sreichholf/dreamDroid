package net.reichholf.dreamdroid.helpers

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import net.reichholf.dreamdroid.data.ProfileRepository
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
        val profile = ProfileRepository.get().requireCurrent()
        try {
            var tmpFile = File(localPath)
            if (!tmpFile.exists()) {
                tmpFile.mkdirs()
            }
            tmpFile = File("$localPath.nomedia")
            if (!tmpFile.exists()) {
                tmpFile.createNewFile()
            }

            if (remotePath.isNullOrBlank()) {
                failFtp(PiconFtpSync.directoryGate(remotePath, false, 0, null))
                return
            }

            publish(R.string.connecting)
            client.connect(profile.host)
            if (isStopped) {
                return
            }
            publish(R.string.connected)
            val loggedIn = client.login(profile.user, profile.pass)
            if (failFtp(PiconFtpSync.loginGate(loggedIn, client.replyCode, client.replyString))) {
                return
            }
            if (isStopped) {
                return
            }
            publish(R.string.connected)

            client.setFileType(FTPClient.BINARY_FILE_TYPE)
            Log.i(TAG, "Changing to $remotePath")
            val cwdOk = client.changeWorkingDirectory(remotePath)
            if (
                failFtp(
                    PiconFtpSync.directoryGate(
                        remotePath,
                        cwdOk,
                        client.replyCode,
                        client.replyString
                    )
                )
            ) {
                return
            }
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
                if (saveRetrievedPicon(client, fileName, localPath)) {
                    progress.downloadedFiles++
                }
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

    /** @return true when [step] failed and the sync must stop before listing or downloading. */
    private fun failFtp(step: PiconFtpSync.FtpStep): Boolean {
        if (step is PiconFtpSync.FtpStep.Failed) {
            Log.e(TAG, step.message)
            progress.error = true
            progress.errorText = step.message
            return true
        }
        return false
    }

    /**
     * Downloads one picon via a partial file. A failed retrieve deletes that partial
     * and leaves any existing png in place.
     */
    private fun saveRetrievedPicon(
        client: FTPClient,
        fileName: String,
        localPath: String
    ): Boolean {
        val destination = File(localPath, fileName)
        val partial = File(localPath, "$fileName.part")
        partial.delete()
        var retrieved = false
        var retrieveThrew = false
        try {
            BufferedOutputStream(FileOutputStream(partial)).use { outputStream ->
                retrieved = client.retrieveFile(fileName, outputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download picon with filename $fileName", e)
            retrieved = false
            retrieveThrew = true
        }
        return when (PiconFtpSync.localFileAfterRetrieve(retrieved, client.replyCode)) {
            PiconFtpSync.LocalFile.DISCARD -> {
                partial.delete()
                if (!retrieveThrew) {
                    Log.e(TAG, "Picon retrieve failed for $fileName: ${client.replyString}")
                }
                false
            }

            PiconFtpSync.LocalFile.KEEP -> commitPartial(partial, destination)
        }
    }

    private fun commitPartial(partial: File, destination: File): Boolean {
        if (destination.exists() && !destination.delete()) {
            Log.e(TAG, "Failed to replace picon ${destination.name}")
            partial.delete()
            return false
        }
        if (!partial.renameTo(destination)) {
            Log.e(TAG, "Failed to store picon ${destination.name}")
            partial.delete()
            return false
        }
        return true
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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
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
