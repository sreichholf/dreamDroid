package net.reichholf.dreamdroid.helpers

import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/** Whose picons to sync: the receiver, its login, and the picon folder on it. */
data class PiconSyncTarget(
    val host: String,
    val user: String?,
    val pass: String?,
    val remotePath: String?
)

/** What the sync is doing, so the worker can put it into the foreground notification. */
sealed interface PiconSyncEvent {
    data object Connecting : PiconSyncEvent

    data object Connected : PiconSyncEvent

    data object Listing : PiconSyncEvent

    data object Checking : PiconSyncEvent

    data class Downloading(val fileName: String) : PiconSyncEvent
}

/** A step plus the counts the progress bar needs. */
data class PiconSyncProgress(
    val event: PiconSyncEvent,
    val totalFiles: Int = 0,
    val downloadedFiles: Int = 0
)

/** How a sync ended: the picons stored, and the message that stopped it, if any. */
data class PiconSyncResult(val downloadedFiles: Int, val error: String?)

/**
 * One picon sync into [localDir]: the `.png` files the receiver lists over [ftp], each
 * downloaded to a partial file and renamed when the transfer completed. An existing picon
 * survives a failed transfer.
 *
 * [onProgress] runs before every step, and [isStopped] between them, so a cancelled worker
 * stops downloading instead of finishing the folder.
 */
class PiconSyncJob(
    private val ftp: PiconFtp,
    private val localDir: File,
    private val target: PiconSyncTarget,
    private val isStopped: () -> Boolean,
    private val onProgress: suspend (PiconSyncProgress) -> Unit
) {
    private var totalFiles = 0
    private var downloadedFiles = 0

    suspend fun run(): PiconSyncResult = try {
        sync()
    } catch (e: Exception) {
        Log.e(TAG, "Picon sync failed", e)
        PiconSyncResult(downloadedFiles, e.message)
    } finally {
        ftp.disconnect()
    }

    private suspend fun sync(): PiconSyncResult {
        localDir.mkdirs()
        File(localDir, NOMEDIA).createNewFile()

        val remotePath = target.remotePath
        if (remotePath.isNullOrBlank()) {
            return failed(PiconFtpSync.directoryGate(remotePath, false, 0, null))
        }

        publish(PiconSyncEvent.Connecting)
        ftp.connect(target.host)
        if (isStopped()) {
            return PiconSyncResult(downloadedFiles, null)
        }
        publish(PiconSyncEvent.Connected)
        val login = ftp.login(target.user, target.pass)
        failedOrNull(PiconFtpSync.loginGate(login.ok, login.code, login.text))?.let {
            return PiconSyncResult(downloadedFiles, it)
        }
        if (isStopped()) {
            return PiconSyncResult(downloadedFiles, null)
        }

        publish(PiconSyncEvent.Connected)
        val directory = ftp.changeDirectory(remotePath)
        failedOrNull(
            PiconFtpSync.directoryGate(remotePath, directory.ok, directory.code, directory.text)
        )?.let {
            return PiconSyncResult(downloadedFiles, it)
        }

        publish(PiconSyncEvent.Listing)
        val picons = ftp.listPicons()
        totalFiles = picons.size
        publish(PiconSyncEvent.Checking)
        for (name in picons) {
            if (isStopped()) {
                return PiconSyncResult(downloadedFiles, null)
            }
            publish(PiconSyncEvent.Downloading(name))
            if (store(name)) {
                downloadedFiles++
            }
        }
        return PiconSyncResult(downloadedFiles, null)
    }

    /**
     * Downloads one picon through a partial file. A failed retrieve deletes that partial
     * and leaves any existing png in place.
     */
    private fun store(fileName: String): Boolean {
        val destination = File(localDir, fileName)
        val partial = File(localDir, "$fileName.part")
        partial.delete()
        val reply = try {
            BufferedOutputStream(FileOutputStream(partial)).use { output ->
                ftp.retrieve(fileName, output)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download picon with filename $fileName", e)
            FtpReply(ok = false, code = 0, text = null)
        }
        return when (PiconFtpSync.localFileAfterRetrieve(reply.ok, reply.code)) {
            PiconFtpSync.LocalFile.DISCARD -> {
                partial.delete()
                reply.text?.let { Log.e(TAG, "Picon retrieve failed for $fileName: $it") }
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

    private suspend fun publish(event: PiconSyncEvent) {
        onProgress(
            PiconSyncProgress(
                event = event,
                totalFiles = totalFiles,
                downloadedFiles = downloadedFiles
            )
        )
    }

    /** The failure message of [step], logged; null when the step passed. */
    private fun failedOrNull(step: PiconFtpSync.FtpStep): String? {
        val failure = step as? PiconFtpSync.FtpStep.Failed ?: return null
        Log.e(TAG, failure.message)
        return failure.message
    }

    private fun failed(step: PiconFtpSync.FtpStep): PiconSyncResult =
        PiconSyncResult(downloadedFiles, failedOrNull(step))

    private companion object {
        const val TAG = "PiconSyncJob"
        const val NOMEDIA = ".nomedia"
    }
}
