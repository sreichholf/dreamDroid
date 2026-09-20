package net.reichholf.dreamdroid.helpers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager

/**
 * Enqueues unique picon FTP sync via WorkManager. [ExistingWorkPolicy.KEEP] so a
 * second Settings tap does not restart an in-flight sync.
 */
object PiconSync {
    const val UNIQUE_WORK_NAME: String = "picon_sync"
    const val NOTIFICATION_ID: Int = 0x9923
    const val COMPLETION_NOTIFICATION_ID: Int = 0x9924
    const val CHANNEL_ID: String = "dreamdroid_picon_sync"

    /** True when unique work is waiting on constraints or actively running. */
    fun isWorkInProgress(states: Collection<WorkInfo.State>): Boolean = states.any { state ->
        state == WorkInfo.State.ENQUEUED ||
            state == WorkInfo.State.RUNNING ||
            state == WorkInfo.State.BLOCKED
    }

    fun isRunning(context: Context): Boolean {
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
            .get()
        return isWorkInProgress(infos.map { it.state })
    }

    /**
     * @return true when a new sync was enqueued, false when one is already active.
     */
    fun enqueue(context: Context): Boolean {
        if (isRunning(context)) {
            return false
        }
        val request = OneTimeWorkRequestBuilder<PiconSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        return true
    }
}
