package net.reichholf.dreamdroid.helpers

import org.apache.commons.net.ftp.FTPReply

/**
 * Interprets FTP login, CWD, and retrieve results for picon sync.
 * [org.apache.commons.net.ftp.FTPClient] is concrete, so [PiconSyncWorker] calls this
 * instead of being unit-tested itself.
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
        if (path.isNotEmpty() && cwdSucceeded && FTPReply.isPositiveCompletion(replyCode)) {
            return FtpStep.Ok
        }
        val summary = if (path.isEmpty()) {
            "Remote picon path is empty"
        } else {
            "Could not change to remote picon directory: $path"
        }
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

    private fun failure(summary: String, replyCode: Int, replyText: String?): String {
        val detail = replyText?.replace("\r", "")?.replace("\n", " ")?.trim().orEmpty()
        return when {
            detail.isNotEmpty() -> "$summary ($detail)"
            replyCode > 0 -> "$summary ($replyCode)"
            else -> summary
        }
    }
}
