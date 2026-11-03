package net.reichholf.dreamdroid.helpers

import java.io.File
import org.apache.commons.net.ftp.FTPReply

/**
 * Interprets the login, CWD and retrieve replies of a picon sync: what [PiconSyncJob] makes
 * of an [FtpReply] before it lists or stores anything.
 */
object PiconFtpSync {
    sealed class FtpStep {
        data object Ok : FtpStep()

        data class Failed(val message: String) : FtpStep()

        /** False means do not list or download. */
        val proceed: Boolean
            get() = this is Ok
    }

    /** What to do with a local file opened for one RETR. */
    enum class LocalFile {
        KEEP,
        DISCARD
    }

    fun loginGate(loginSucceeded: Boolean, replyCode: Int, replyText: String?): FtpStep {
        if (loginSucceeded && FTPReply.isPositiveCompletion(replyCode)) {
            return FtpStep.Ok
        }
        return FtpStep.Failed(failure("FTP login failed", replyCode, replyText))
    }

    /**
     * Blank [remotePath] fails the same way as a CWD that did not succeed:
     * the caller must not list or download. Placeholders such as `{Speicher}`
     * are not rewritten; they are sent to the server as configured.
     */
    fun directoryGate(
        remotePath: String?,
        cwdSucceeded: Boolean,
        replyCode: Int,
        replyText: String?
    ): FtpStep {
        val path = remotePath?.trim().orEmpty()
        if (path.isEmpty()) {
            return FtpStep.Failed("Remote picon path is empty")
        }
        if (cwdSucceeded && FTPReply.isPositiveCompletion(replyCode)) {
            return FtpStep.Ok
        }
        val summary = "Could not change to remote picon directory: $path"
        return FtpStep.Failed(failure(summary, replyCode, replyText))
    }

    /**
     * A failed retrieve must not keep the local file. Callers create that file
     * before RETR, so [LocalFile.DISCARD] means delete it (including an empty png).
     */
    fun localFileAfterRetrieve(retrieveSucceeded: Boolean, replyCode: Int): LocalFile {
        val completed = retrieveSucceeded && FTPReply.isPositiveCompletion(replyCode)
        return if (completed) LocalFile.KEEP else LocalFile.DISCARD
    }

    /**
     * [remoteName] when the sync may store it in the picon folder, null when it must skip
     * the entry. The listing comes from the receiver, so it is not trusted: a path
     * separator or an absolute path would put the file outside the folder, and the `..`
     * segment would climb out of it. A name that merely contains two dots (`ard..png`)
     * is a normal file name and stays.
     */
    fun localFileName(remoteName: String): String? {
        if (remoteName.isEmpty()) return null
        if (remoteName.contains('/') || remoteName.contains('\\')) return null
        if (File(remoteName).isAbsolute) return null
        // Separators are rejected above, so the name is a single segment and only the
        // segment `..` itself can leave the folder.
        if (remoteName == "..") return null
        return remoteName
    }

    private fun failure(summary: String, replyCode: Int, replyText: String?): String {
        val detail = replyText?.replace("\r", "")?.replace("\n", " ")?.trim().orEmpty()
        return when {
            detail.isNotEmpty() -> "$summary ($detail)"
            replyCode > 0 -> "$summary ($replyCode)"
            else -> summary
        }
    }
}
