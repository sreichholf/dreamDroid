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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.helpers.enigma2.Picon

/**
 * Runs [PiconSyncJob] as a foreground worker, so a long download survives the user leaving
 * Settings: the sync reports each step here and this turns it into the notification.
 */
class PiconSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private var totalFiles: Int = 0
    private var downloadedFiles: Int = 0

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(createForegroundInfo(applicationContext.getString(R.string.connecting)))
        val result = try {
            syncPicons()
        } catch (e: Exception) {
            Log.e(TAG, "Picon sync failed", e)
            PiconSyncResult(0, 0, e.message)
        }
        publishFinished(result)
        if (result.error != null || result.downloadedNothing || isStopped) {
            Result.failure()
        } else {
            Result.success()
        }
    }

    private suspend fun syncPicons(): PiconSyncResult {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PiconSyncWorkerEntryPoint::class.java
        )
        val profiles = entryPoint.profileRepository()
        // WorkManager can start this in a fresh process, before the active profile is read.
        profiles.awaitLoaded()
        // No screen is open while this runs, so nothing gates it when the last profile is gone.
        val profile = profiles.current.value
            ?: return PiconSyncResult(0, 0, applicationContext.getString(R.string.no_profile_title))
        val host = profile.host?.takeIf { it.isNotEmpty() }
            ?: return PiconSyncResult(0, 0, applicationContext.getString(R.string.error))
        val remotePath = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getString(DreamDroid.PREFS_KEY_SYNC_PICONS_PATH, DEFAULT_REMOTE_PATH)
        val localDir = File(Picon.localDir(applicationContext))
        Log.i(TAG, "Syncing from $remotePath to $localDir")
        val job = PiconSyncJob(
            ftp = entryPoint.piconFtp(),
            localDir = localDir,
            target = PiconSyncTarget(host, profile.user, profile.pass, remotePath),
            isStopped = { isStopped },
            onProgress = { progress -> publish(progress) }
        )
        return job.run()
    }

    private suspend fun publish(progress: PiconSyncProgress) {
        totalFiles = progress.totalFiles
        downloadedFiles = progress.downloadedFiles
        setForeground(createForegroundInfo(message(progress)))
    }

    private fun message(progress: PiconSyncProgress): String = when (val event = progress.event) {
        PiconSyncEvent.Connecting -> applicationContext.getString(R.string.connecting)
        PiconSyncEvent.Connected -> applicationContext.getString(R.string.connected)
        PiconSyncEvent.Listing -> applicationContext.getString(R.string.getting_list_of_files)
        PiconSyncEvent.Checking -> applicationContext.getString(R.string.checking)
        is PiconSyncEvent.Downloading -> event.fileName
    }

    private fun publishFinished(result: PiconSyncResult) {
        Picon.clearCache(applicationContext)
        val message = when {
            result.error != null -> result.error.takeIf { it.isNotEmpty() }
                ?: applicationContext.getString(R.string.error)

            result.downloadedNothing ->
                applicationContext.getString(R.string.picon_sync_failed)

            else -> applicationContext.getString(
                R.string.picon_sync_finished,
                result.downloadedFiles
            )
        }
        // Lint needs the permission check in the same method as the notify call below.
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
        if (totalFiles > 0 && ongoing) {
            builder.setProgress(totalFiles, downloadedFiles, false)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "PiconSyncWorker"
        private const val DEFAULT_REMOTE_PATH = "/usr/share/enigma2/picon"
    }
}

/** The worker's dependencies (decision 7 in docs/hilt-migration.md). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PiconSyncWorkerEntryPoint {
    fun profileRepository(): ProfileRepository

    /** One client per sync; the sync closes it. */
    fun piconFtp(): PiconFtp
}
