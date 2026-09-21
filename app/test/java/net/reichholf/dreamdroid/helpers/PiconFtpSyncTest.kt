package net.reichholf.dreamdroid.helpers

import org.apache.commons.net.ftp.FTPReply
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PiconFtpSyncTest {
    @Test
    fun cwdFailedDoesNotDownload() {
        val step = PiconFtpSync.directoryGate(
            remotePath = "/usr/share/enigma2/picon",
            cwdSucceeded = false,
            replyCode = FTPReply.FILE_UNAVAILABLE,
            replyText = "550 No such file or directory"
        )
        assertFalse(step.proceed)
        val failed = step as PiconFtpSync.FtpStep.Failed
        assertTrue(failed.message.contains("/usr/share/enigma2/picon"))
        assertTrue(failed.message.contains("550"))
    }

    @Test
    fun cwdReplyNotPositiveDoesNotDownload() {
        val step = PiconFtpSync.directoryGate(
            remotePath = "/usr/share/enigma2/picon",
            cwdSucceeded = true,
            replyCode = FTPReply.FILE_UNAVAILABLE,
            replyText = "550 No such file or directory"
        )
        assertFalse(step.proceed)
    }

    @Test
    fun cwdBooleanFalseDoesNotDownloadEvenWithOkReply() {
        val step = PiconFtpSync.directoryGate(
            remotePath = "/usr/share/enigma2/picon",
            cwdSucceeded = false,
            replyCode = FTPReply.FILE_ACTION_OK,
            replyText = "250 Directory successfully changed."
        )
        assertFalse(step.proceed)
    }

    @Test
    fun cwdSucceededAllowsDownload() {
        val step = PiconFtpSync.directoryGate(
            remotePath = "/usr/share/enigma2/picon",
            cwdSucceeded = true,
            replyCode = FTPReply.FILE_ACTION_OK,
            replyText = "250 Directory successfully changed."
        )
        assertTrue(step.proceed)
        assertTrue(step is PiconFtpSync.FtpStep.Ok)
    }

    @Test
    fun blankRemotePathDoesNotDownload() {
        val empty = PiconFtpSync.directoryGate(
            remotePath = "",
            cwdSucceeded = true,
            replyCode = FTPReply.FILE_ACTION_OK,
            replyText = null
        )
        val blank = PiconFtpSync.directoryGate(
            remotePath = "   ",
            cwdSucceeded = true,
            replyCode = FTPReply.FILE_ACTION_OK,
            replyText = null
        )
        val missing = PiconFtpSync.directoryGate(
            remotePath = null,
            cwdSucceeded = true,
            replyCode = FTPReply.FILE_ACTION_OK,
            replyText = null
        )
        assertFalse(empty.proceed)
        assertFalse(blank.proceed)
        assertFalse(missing.proceed)
        assertEquals(
            "Remote picon path is empty",
            (empty as PiconFtpSync.FtpStep.Failed).message
        )
    }

    @Test
    fun placeholderPathIsLeftUnchangedWhenCwdFails() {
        val path = "/{Speicher}/dreamDroid/picons"
        val step = PiconFtpSync.directoryGate(
            remotePath = path,
            cwdSucceeded = false,
            replyCode = FTPReply.FILE_UNAVAILABLE,
            replyText = "550 No such file or directory"
        ) as PiconFtpSync.FtpStep.Failed
        assertFalse(step.proceed)
        assertTrue(step.message.contains(path))
    }

    @Test
    fun loginFailedDoesNotContinue() {
        val step = PiconFtpSync.loginGate(
            loginSucceeded = false,
            replyCode = FTPReply.NOT_LOGGED_IN,
            replyText = "530 Login incorrect."
        )
        assertFalse(step.proceed)
        assertTrue((step as PiconFtpSync.FtpStep.Failed).message.contains("530"))
    }

    @Test
    fun loginReplyNotPositiveDoesNotContinue() {
        val step = PiconFtpSync.loginGate(
            loginSucceeded = true,
            replyCode = FTPReply.NOT_LOGGED_IN,
            replyText = "530 Not logged in."
        )
        assertFalse(step.proceed)
    }

    @Test
    fun loginSucceededContinues() {
        val step = PiconFtpSync.loginGate(
            loginSucceeded = true,
            replyCode = FTPReply.USER_LOGGED_IN,
            replyText = "230 Login successful."
        )
        assertTrue(step.proceed)
    }

    @Test
    fun retrieveFailedDoesNotKeepEmptyFile() {
        assertEquals(
            PiconFtpSync.LocalFile.DISCARD,
            PiconFtpSync.localFileAfterRetrieve(
                retrieveSucceeded = false,
                replyCode = FTPReply.FILE_UNAVAILABLE
            )
        )
        assertEquals(
            PiconFtpSync.LocalFile.DISCARD,
            PiconFtpSync.localFileAfterRetrieve(
                retrieveSucceeded = false,
                replyCode = FTPReply.CLOSING_DATA_CONNECTION
            )
        )
        assertEquals(
            PiconFtpSync.LocalFile.DISCARD,
            PiconFtpSync.localFileAfterRetrieve(
                retrieveSucceeded = true,
                replyCode = FTPReply.FILE_UNAVAILABLE
            )
        )
    }

    @Test
    fun retrieveSucceededKeepsFile() {
        assertEquals(
            PiconFtpSync.LocalFile.KEEP,
            PiconFtpSync.localFileAfterRetrieve(
                retrieveSucceeded = true,
                replyCode = FTPReply.CLOSING_DATA_CONNECTION
            )
        )
    }
}
