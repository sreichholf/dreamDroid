package net.reichholf.dreamdroid.helpers

import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

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

/** How a sync ended: how many picons it tried to download, how many arrived, and why not. */
data class PiconSyncResult(val totalFiles: Int, val downloadedFiles: Int, val error: String?) {
    /** The sync tried picons and not one of them could be stored. */
    val downloadedNothing: Boolean
        get() = error == null && totalFiles > 0 && downloadedFiles == 0
}

/**
 * One picon sync into [localDir]: the `.png` files the receiver lists over [ftp], each
 * downloaded to a partial file and renamed when the transfer completed. An existing picon
 * survives a failed transfer.
 *
 * One sync downloads at most `MAX_PICON_FILES` picons and one transfer at most
 * `MAX_PICON_BYTES`, so a receiver that lists a huge folder or serves a huge file cannot keep
 * the sync running. A larger folder is synced up to the cap and reported as incomplete.
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
        PiconSyncResult(totalFiles, downloadedFiles, e.message)
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
            return PiconSyncResult(totalFiles, downloadedFiles, null)
        }
        publish(PiconSyncEvent.Connected)
        val login = ftp.login(target.user, target.pass)
        failedOrNull(PiconFtpSync.loginGate(login.ok, login.code, login.text))?.let {
            return PiconSyncResult(totalFiles, downloadedFiles, it)
        }
        if (isStopped()) {
            return PiconSyncResult(totalFiles, downloadedFiles, null)
        }

        publish(PiconSyncEvent.Connected)
        val directory = ftp.changeDirectory(remotePath)
        failedOrNull(
            PiconFtpSync.directoryGate(remotePath, directory.ok, directory.code, directory.text)
        )?.let {
            return PiconSyncResult(totalFiles, downloadedFiles, it)
        }

        publish(PiconSyncEvent.Listing)
        val listed = ftp.listPicons()
        val picons = listed.mapNotNull(PiconFtpSync::localFileName)
        if (picons.isEmpty() && listed.isNotEmpty()) {
            return PiconSyncResult(
                0,
                0,
                "The receiver listed ${listed.size} picon names that are not safe file names"
            )
        }
        val rejected = listed.size - picons.size
        if (rejected > 0) {
            Log.w(TAG, "Skipping $rejected listed picons with unsafe file names")
        }
        totalFiles = picons.size
        if (picons.size > MAX_PICON_FILES) {
            Log.w(TAG, "Syncing the first $MAX_PICON_FILES of ${picons.size} listed picons")
        }
        publish(PiconSyncEvent.Checking)
        for (name in picons.take(MAX_PICON_FILES)) {
            if (isStopped()) {
                return PiconSyncResult(totalFiles, downloadedFiles, null)
            }
            publish(PiconSyncEvent.Downloading(name))
            if (store(name)) {
                downloadedFiles++
            }
        }
        return PiconSyncResult(totalFiles, downloadedFiles, null)
    }

    /**
     * Downloads one picon through a partial file. A failed retrieve deletes that partial
     * and leaves any existing png in place. A name that does not resolve to a file directly
     * inside [localDir] is skipped without a retrieve.
     */
    private fun store(name: String): Boolean {
        val destination = localDestination(name)
        if (destination == null) {
            Log.e(TAG, "Refusing picon that resolves outside the picon folder: $name")
            return false
        }
        val partial = File(destination.parentFile, "${destination.name}.part")
        partial.delete()
        val reply = try {
            BufferedOutputStream(FileOutputStream(partial)).use { file ->
                ftp.retrieve(name, SizeLimitedOutputStream(file, MAX_PICON_BYTES))
            }
        } catch (e: Exception) {
            partial.delete()
            if (generateSequence<Throwable>(e) { it.cause }.any { it is PiconTooLargeException }) {
                throw IOException("Picon $name is larger than $MAX_PICON_BYTES bytes", e)
            }
            Log.e(TAG, "Failed to download picon with filename $name", e)
            FtpReply(ok = false, code = 0, text = null)
        }
        return when (PiconFtpSync.localFileAfterRetrieve(reply.ok, reply.code)) {
            PiconFtpSync.LocalFile.DISCARD -> {
                partial.delete()
                reply.text?.let { Log.e(TAG, "Picon retrieve failed for $name: $it") }
                false
            }

            PiconFtpSync.LocalFile.KEEP -> commitPartial(partial, destination)
        }
    }

    /**
     * [name] inside the canonical [localDir], or null when a symlink in the folder points
     * outside it. [name] is one safe segment (see [PiconFtpSync.localFileName]), so symlink
     * resolution is the only way it can leave the folder.
     */
    private fun localDestination(name: String): File? = try {
        val dir = localDir.canonicalFile
        val file = File(dir, name).canonicalFile
        file.takeIf { it.parentFile == dir }
    } catch (e: IOException) {
        Log.e(TAG, "Could not resolve the local path for picon $name", e)
        null
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
        PiconSyncResult(totalFiles, downloadedFiles, failedOrNull(step))

    internal companion object {
        /** One sync downloads at most this many picons; the rest of the folder is skipped. */
        const val MAX_PICON_FILES = 50_000

        /** A picon is a small PNG; a transfer past this fails the sync. */
        const val MAX_PICON_BYTES = 8L * 1024 * 1024

        private const val TAG = "PiconSyncJob"
        private const val NOMEDIA = ".nomedia"
    }
}

/** Writes at most [maxBytes]; a longer transfer fails instead of filling the disk. */
private class SizeLimitedOutputStream(
    private val delegate: OutputStream,
    private val maxBytes: Long
) : OutputStream() {
    private var written = 0L

    override fun write(b: Int) {
        checkRoom(1)
        delegate.write(b)
        written++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        checkRoom(len)
        delegate.write(b, off, len)
        written += len
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }

    private fun checkRoom(len: Int) {
        if (written + len > maxBytes) {
            throw PiconTooLargeException("Picon exceeds the $maxBytes byte limit")
        }
    }
}

/**
 * A transfer that hit the size cap. Its own type, so [PiconSyncJob] fails the sync instead of
 * skipping the file on a control connection the aborted transfer left out of sync. FTP
 * clients may wrap it (commons-net throws a `CopyStreamException` with it as the cause).
 */
private class PiconTooLargeException(message: String) : IOException(message)
