package net.reichholf.dreamdroid.helpers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
}

/** Starts the picon FTP sync. An interface because WorkManager does not run on the JVM. */
interface PiconSyncScheduler {
    /** @return true when a new sync was enqueued, false when one is already active. */
    suspend fun enqueue(): Boolean
}

/**
 * Enqueues unique picon FTP sync via WorkManager. [ExistingWorkPolicy.KEEP] so a
 * second Settings tap does not restart an in-flight sync.
 */
class WorkManagerPiconSync @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PiconSyncScheduler {
    override suspend fun enqueue(): Boolean = withContext(Dispatchers.IO) {
        val workManager = WorkManager.getInstance(context)
        val infos = workManager.getWorkInfosForUniqueWork(PiconSync.UNIQUE_WORK_NAME).get()
        if (PiconSync.isWorkInProgress(infos.map { it.state })) {
            return@withContext false
        }
        val request = OneTimeWorkRequestBuilder<PiconSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            PiconSync.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        true
    }
}
