package net.reichholf.dreamdroid.helpers

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The sync itself, against a fake [PiconFtp] and a real folder: what it stores, which steps
 * it reports, and where it stops when the server or the caller says stop.
 */
class PiconSyncJobTest {
    @TempDir
    lateinit var dir: File

    private val ftp = FakePiconFtp()
    private val events = mutableListOf<PiconSyncProgress>()

    @Test
    fun syncsEveryPngIntoTheFolder() {
        ftp.picons = listOf("ard.png", "zdf.png")
        ftp.contents = mapOf("ard.png" to ARD, "zdf.png" to ZDF)

        val result = runBlocking { job().run() }

        assertEquals(PiconSyncResult(2, 2, null), result)
        assertArrayEquals(ARD, File(dir, "ard.png").readBytes())
        assertArrayEquals(ZDF, File(dir, "zdf.png").readBytes())
        assertEquals(listOf("ard.png", "zdf.png"), ftp.retrieved)
        assertTrue(File(dir, NOMEDIA).exists(), "the picon folder must stay out of the gallery")
    }

    @Test
    fun reportsTheStepsItWalksThrough() {
        ftp.picons = listOf("ard.png")
        ftp.contents = mapOf("ard.png" to ARD)

        runBlocking { job().run() }

        assertEquals(
            listOf(
                PiconSyncEvent.Connecting,
                PiconSyncEvent.Connected,
                PiconSyncEvent.Connected,
                PiconSyncEvent.Listing,
                PiconSyncEvent.Checking,
                PiconSyncEvent.Downloading("ard.png")
            ),
            events.map { it.event }
        )
        assertEquals(1, events.last().totalFiles)
        assertEquals(0, events.last().downloadedFiles)
    }

    @Test
    fun logsInWithTheProfileAndEndsTheSession() {
        runBlocking { job().run() }

        assertEquals(HOST, ftp.host)
        assertEquals(USER to PASS, ftp.credentials)
        assertEquals(REMOTE_PATH, ftp.directory)
        assertTrue(ftp.disconnected, "the session must be closed")
        assertFalse(ftp.connected)
    }

    @Test
    fun aRejectedLoginStopsBeforeListing() {
        ftp.loginReply = FtpReply(false, 530, "Login incorrect")

        val result = runBlocking { job().run() }

        assertNotNull(result.error)
        assertEquals(0, result.downloadedFiles)
        assertNull(ftp.directory, "a failed login must not change directory")
        assertTrue(ftp.retrieved.isEmpty())
    }

    @Test
    fun aBlankRemotePathFailsBeforeConnecting() {
        val result = runBlocking { job(remotePath = "  ").run() }

        assertEquals("Remote picon path is empty", result.error)
        assertNull(ftp.host)
    }

    @Test
    fun aFailedDirectoryChangeStopsBeforeListing() {
        ftp.directoryReply = FtpReply(false, 550, "Failed to change directory")

        val result = runBlocking { job().run() }

        assertTrue(
            result.error?.contains("Could not change to remote picon directory") == true,
            "the CWD failure must be reported: ${result.error}"
        )
        assertTrue(ftp.retrieved.isEmpty())
    }

    @Test
    fun aFailedRetrieveKeepsTheExistingPngAndDropsThePartial() {
        File(dir, "ard.png").writeBytes(EXISTING)
        ftp.picons = listOf("ard.png")
        ftp.retrieveReply = FtpReply(false, 550, "No such file")

        val result = runBlocking { job().run() }

        assertEquals(0, result.downloadedFiles)
        assertArrayEquals(EXISTING, File(dir, "ard.png").readBytes())
        assertFalse(File(dir, "ard.png.part").exists())
    }

    @Test
    fun aSyncWhereNoPiconArrivedIsAnError() {
        ftp.picons = listOf("ard.png", "zdf.png")
        ftp.retrieveReply = FtpReply(false, 550, "No such file")

        val result = runBlocking { job().run() }

        assertEquals(PiconSyncResult(2, 0, null), result)
        assertTrue(result.downloadedNothing, "a sync that stored nothing must be an error")
    }

    @Test
    fun aSyncThatStoredSomePiconsIsNotAnError() {
        ftp.picons = listOf("ard.png", "zdf.png")
        ftp.contents = mapOf("ard.png" to ARD)
        ftp.failRetrieveFor = setOf("zdf.png")

        val result = runBlocking { job().run() }

        assertEquals(PiconSyncResult(2, 1, null), result)
        assertFalse(result.downloadedNothing, "one stored picon is not a failed sync")
    }

    @Test
    fun aListingEntryThatEscapesTheFolderIsSkipped() {
        val escape = File(dir.parentFile, "${dir.name}-escape.png")
        ftp.picons = listOf(
            "../${escape.name}",
            "sub/ard.png",
            File(dir, "absolute.png").absolutePath
        )

        val result = runBlocking { job().run() }

        assertEquals(0, result.downloadedFiles)
        assertTrue(
            result.downloadedNothing,
            "a listing outside the folder must not count as stored"
        )
        assertTrue(ftp.retrieved.isEmpty(), "an unsafe name must not reach RETR")
        assertFalse(escape.exists())
        assertFalse(File(dir, "absolute.png").exists())
    }

    @Test
    fun aSymlinkInTheFolderIsNotFollowed() {
        val outside = File(dir.parentFile, "${dir.name}-outside.png")
            .apply { writeBytes(EXISTING) }
        val linked = try {
            Files.createSymbolicLink(File(dir, "ard.png").toPath(), outside.toPath())
            true
        } catch (e: IOException) {
            false
        } catch (e: UnsupportedOperationException) {
            false
        }
        assumeTrue(linked, "symlinks are not supported here")
        ftp.picons = listOf("ard.png")
        ftp.contents = mapOf("ard.png" to ARD)

        val result = runBlocking { job().run() }

        assertEquals(0, result.downloadedFiles)
        assertArrayEquals(EXISTING, outside.readBytes(), "a symlink must not be followed")
        outside.delete()
    }

    @Test
    fun aStoppedSyncDoesNotDownloadOn() {
        ftp.picons = listOf("ard.png", "zdf.png")
        ftp.contents = mapOf("ard.png" to ARD, "zdf.png" to ZDF)

        val result = runBlocking {
            // Stops as soon as the first picon has been retrieved.
            PiconSyncJob(
                ftp = ftp,
                localDir = dir,
                target = PiconSyncTarget(HOST, USER, PASS, REMOTE_PATH),
                isStopped = { ftp.retrieved.isNotEmpty() },
                onProgress = { events += it }
            ).run()
        }

        assertEquals(1, result.downloadedFiles)
        assertNull(result.error)
        assertFalse(File(dir, "zdf.png").exists())
    }

    @Test
    fun anExceptionWhileListingIsReported() {
        ftp.listingThrows = IOException("socket closed")

        val result = runBlocking { job().run() }

        assertEquals("socket closed", result.error)
        assertTrue(ftp.disconnected, "the session must be closed on failure")
    }

    @Test
    fun aFailedConnectionIsReported() {
        ftp.connectThrows = IOException("connection refused")

        val result = runBlocking { job().run() }

        assertEquals("connection refused", result.error)
        assertTrue(ftp.disconnected)
    }

    private fun job(remotePath: String? = REMOTE_PATH) = PiconSyncJob(
        ftp = ftp,
        localDir = dir,
        target = PiconSyncTarget(HOST, USER, PASS, remotePath),
        isStopped = { false },
        onProgress = { events += it }
    )

    private companion object {
        const val HOST = "box.local"
        const val USER = "root"
        const val PASS = "secret"
        const val REMOTE_PATH = "/usr/share/enigma2/picon"
        const val NOMEDIA = ".nomedia"

        val ARD = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val ZDF = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 4, 5, 6)
        val EXISTING = byteArrayOf(9, 9, 9)
    }
}

/** A receiver that answers from memory; every step can be made to fail. */
private class FakePiconFtp : PiconFtp {
    var host: String? = null
        private set
    var credentials: Pair<String?, String?>? = null
        private set
    var directory: String? = null
        private set
    var connected: Boolean = false
        private set
    var disconnected: Boolean = false
        private set
    val retrieved = mutableListOf<String>()

    var picons: List<String> = emptyList()
    var contents: Map<String, ByteArray> = emptyMap()
    var loginReply = FtpReply(ok = true, code = 230, text = "User logged in")
    var directoryReply = FtpReply(ok = true, code = 250, text = "Directory changed")
    var retrieveReply = FtpReply(ok = true, code = 226, text = "Transfer complete")
    var failRetrieveFor: Set<String> = emptySet()
    var connectThrows: Exception? = null
    var listingThrows: Exception? = null

    override fun connect(host: String) {
        connectThrows?.let { throw it }
        this.host = host
        connected = true
    }

    override fun login(user: String?, pass: String?): FtpReply {
        credentials = user to pass
        return loginReply
    }

    override fun changeDirectory(path: String): FtpReply {
        directory = path
        return directoryReply
    }

    override fun listPicons(): List<String> {
        listingThrows?.let { throw it }
        return picons
    }

    override fun retrieve(name: String, to: OutputStream): FtpReply {
        retrieved += name
        if (name in failRetrieveFor) {
            return FtpReply(ok = false, code = 550, text = "No such file")
        }
        if (retrieveReply.ok) {
            contents[name]?.let { to.write(it) }
        }
        return retrieveReply
    }

    override fun disconnect() {
        connected = false
        disconnected = true
    }
}
