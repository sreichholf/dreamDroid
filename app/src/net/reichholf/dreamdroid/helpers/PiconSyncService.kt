package net.reichholf.dreamdroid.helpers

import android.app.IntentService
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFileFilter

/**
 * Created by Stephan on 05.02.2016.
 */
class PiconSyncService : IntentService(PiconSyncService::class.java.canonicalName) {
    private val id = 0x9923

    class DownloadProgress {
        var connected: Boolean = false
        var error: Boolean = false
        var totalFiles: Int = 0
        var downloadedFiles: Int = 0
        var currentFile: String = ""
        var errorText: String? = ""

        companion object {
            const val EVENT_ID_CONNECTING: Int = 0
            const val EVENT_ID_CONNECTED: Int = 1
            const val EVENT_ID_LOGIN_SUCCEEDED: Int = 2
            const val EVENT_ID_LISTING: Int = 3
            const val EVENT_ID_LISTING_READY: Int = 4
            const val EVENT_ID_DOWNLOADING_FILE: Int = 6
            const val EVENT_ID_FINISHED: Int = 7
            const val EVENT_ID_ERROR: Int = 0x99
        }
    }

    protected lateinit var notifyManager: NotificationManagerCompat
    protected lateinit var notificationBuilder: NotificationCompat.Builder
    protected lateinit var downloadProgress: DownloadProgress

    override fun onHandleIntent(intent: Intent?) {
        downloadProgress = DownloadProgress()
        initNotifications()
        syncPicons()
    }

    protected fun syncPicons() {
        val localPath = Picon.getBasepath(applicationContext)
        val remotePath = PreferenceManager.getDefaultSharedPreferences(baseContext)
            .getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, "/usr/share/enigma2/picon")
        Log.i(TAG, String.format("Syncing from %s to %s", remotePath, localPath))
        val client = FTPClient()

        @Suppress("UNUSED_VARIABLE")
        val config = FTPClientConfig()
        val p = DreamDroid.getCurrentProfile()
        try {
            var tmpFile = File(localPath)
            if (!tmpFile.exists()) {
                tmpFile.mkdirs()
            }

            tmpFile = File(String.format("%s.nomedia", localPath))
            if (!tmpFile.exists()) {
                tmpFile.createNewFile()
            }

            publishProgress(DownloadProgress.EVENT_ID_CONNECTING)
            client.connect(p.host)
            publishProgress(DownloadProgress.EVENT_ID_CONNECTED)
            client.login(p.user, p.pass)
            publishProgress(DownloadProgress.EVENT_ID_LOGIN_SUCCEEDED)

            client.setFileType(FTPClient.BINARY_FILE_TYPE)
            Log.i(TAG, String.format("Changing to %s", remotePath))
            client.changeWorkingDirectory(remotePath)
            publishProgress(DownloadProgress.EVENT_ID_LISTING)

            val filter = FTPFileFilter { file -> file.isFile && file.name.endsWith(".png") }
            val fileList = client.listFiles(null, filter)
            downloadProgress.totalFiles = fileList.size
            publishProgress(DownloadProgress.EVENT_ID_LISTING_READY)
            for (remoteFile in fileList) {
                if (!remoteFile.isFile) continue
                val fileName = remoteFile.name

                downloadProgress.currentFile = fileName
                publishProgress(DownloadProgress.EVENT_ID_DOWNLOADING_FILE)

                val localFile = File(String.format("%s%s", localPath, fileName))
                localFile.createNewFile()
                val outputStream = BufferedOutputStream(FileOutputStream(localFile))
                try {
                    client.retrieveFile(fileName, outputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e(TAG, "Failed to download picon with filename $fileName")
                }
                outputStream.close()
                downloadProgress.downloadedFiles++
            }
        } catch (e: Exception) {
            e.printStackTrace()
            downloadProgress.error = true
            downloadProgress.errorText = e.message
            publishProgress(DownloadProgress.EVENT_ID_ERROR)
        }
        publishProgress(DownloadProgress.EVENT_ID_FINISHED)
    }

    fun initNotifications() {
        val context = applicationContext
        notifyManager = NotificationManagerCompat.from(context)
        notificationBuilder = NotificationCompat.Builder(context, "dreamdroid_picon_sync")

        val bm = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        notificationBuilder.setContentTitle(context.getString(R.string.sync_picons))
            .setLargeIcon(bm)
            .setSmallIcon(R.drawable.ic_action_refresh)
    }

    fun publishProgress(eventid: Int) {
        var message = "-"
        when (eventid) {
            DownloadProgress.EVENT_ID_CONNECTING -> message = getString(R.string.connecting)

            DownloadProgress.EVENT_ID_CONNECTED -> message = getString(R.string.connected)

            DownloadProgress.EVENT_ID_LOGIN_SUCCEEDED -> message = getString(R.string.connected)

            DownloadProgress.EVENT_ID_LISTING -> message = getString(R.string.getting_list_of_files)

            DownloadProgress.EVENT_ID_LISTING_READY -> message = getString(R.string.checking)

            DownloadProgress.EVENT_ID_DOWNLOADING_FILE -> message = downloadProgress.currentFile

            DownloadProgress.EVENT_ID_FINISHED -> {
                Picon.clearCache(this)
                if (!downloadProgress.error) {
                    message =
                        getString(R.string.picon_sync_finished, downloadProgress.downloadedFiles)
                } else {
                    message = downloadProgress.errorText ?: downloadProgress.currentFile
                }
            }
        }
        if (downloadProgress.totalFiles > 0) {
            notificationBuilder.setContentText(message)
                .setProgress(downloadProgress.totalFiles, downloadProgress.downloadedFiles, false)
                .setOngoing(true)
        } else {
            notificationBuilder.setContentText(message).setOngoing(true)
        }
        if (eventid == DownloadProgress.EVENT_ID_FINISHED) {
            notificationBuilder.setOngoing(false)
        }
        notifyManager.notify(id, notificationBuilder.build())
    }

    companion object {
        private val TAG = PiconSyncService::class.java.simpleName
    }
}
