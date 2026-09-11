package net.reichholf.dreamdroid.helpers

import android.app.IntentService
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFileFilter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Created by Stephan on 05.02.2016.
 */
class PiconSyncService : IntentService(PiconSyncService::class.java.canonicalName) {
    private val mId = 0x9923

    class DownloadProgress {
        @JvmField var connected: Boolean = false
        @JvmField var error: Boolean = false
        @JvmField var totalFiles: Int = 0
        @JvmField var downloadedFiles: Int = 0
        @JvmField var currentFile: String = ""
        @JvmField var errorText: String? = ""

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

    protected lateinit var mNotifyManager: NotificationManagerCompat
    protected lateinit var mNotificationBuilder: NotificationCompat.Builder
    protected lateinit var mDownloadProgress: DownloadProgress

    override fun onHandleIntent(intent: Intent?) {
        mDownloadProgress = DownloadProgress()
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
            mDownloadProgress.totalFiles = fileList.size
            publishProgress(DownloadProgress.EVENT_ID_LISTING_READY)
            for (remoteFile in fileList) {
                if (!remoteFile.isFile) continue
                val fileName = remoteFile.name

                mDownloadProgress.currentFile = fileName
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
                mDownloadProgress.downloadedFiles++
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mDownloadProgress.error = true
            mDownloadProgress.errorText = e.message
            publishProgress(DownloadProgress.EVENT_ID_ERROR)
        }
        publishProgress(DownloadProgress.EVENT_ID_FINISHED)
    }

    fun initNotifications() {
        val context = applicationContext
        mNotifyManager = NotificationManagerCompat.from(context)
        mNotificationBuilder = NotificationCompat.Builder(context, "dreamdroid_picon_sync")

        val bm = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        mNotificationBuilder.setContentTitle(context.getString(R.string.sync_picons))
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
            DownloadProgress.EVENT_ID_DOWNLOADING_FILE -> message = mDownloadProgress.currentFile
            DownloadProgress.EVENT_ID_FINISHED -> {
                Picon.clearCache()
                if (!mDownloadProgress.error) {
                    message = getString(R.string.picon_sync_finished, mDownloadProgress.downloadedFiles)
                } else {
                    message = mDownloadProgress.errorText ?: mDownloadProgress.currentFile
                }
            }
        }
        if (mDownloadProgress.totalFiles > 0) {
            mNotificationBuilder.setContentText(message)
                .setProgress(mDownloadProgress.totalFiles, mDownloadProgress.downloadedFiles, false)
                .setOngoing(true)
        } else {
            mNotificationBuilder.setContentText(message).setOngoing(true)
        }
        if (eventid == DownloadProgress.EVENT_ID_FINISHED) {
            mNotificationBuilder.setOngoing(false)
        }
        mNotifyManager.notify(mId, mNotificationBuilder.build())
    }

    companion object {
        private val TAG = PiconSyncService::class.java.simpleName
    }
}
