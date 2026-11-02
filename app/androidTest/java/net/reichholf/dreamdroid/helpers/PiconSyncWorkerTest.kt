package net.reichholf.dreamdroid.helpers

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository
import net.reichholf.dreamdroid.data.SettingsRepository
import net.reichholf.dreamdroid.helpers.enigma2.Picon
import net.reichholf.dreamdroid.testutil.FakePiconFtp
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The worker as WorkManager runs it: which profile and remote path it syncs, how a sync's
 * outcome becomes [ListenableWorker.Result], and what the notification says. The sync itself
 * is covered by `PiconSyncJobTest`; the FTP server by [FakePiconFtp], bound by
 * `FakePiconFtpModule`.
 */
@HiltAndroidTest
class PiconSyncWorkerTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var profiles: ProfileRepository

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var ftp: FakePiconFtp

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val localDir = File(Picon.localDir(context))

    private var profileBefore: Profile? = null
    private var pathBefore: String = ""

    @Before
    fun setUp() {
        hiltRule.inject()
        profileBefore = profiles.current.value
        pathBefore = settings.current().syncPiconsPath
        localDir.mkdirs()
        localDir.listFiles()?.forEach { it.delete() }
        // DreamDroid.onCreate creates this channel; the Hilt test application does not run it.
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat
                .Builder(PiconSync.CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Picon sync")
                .build()
        )
        profiles.setCurrent(
            Profile.getDefault().apply {
                name = "Test"
                host = HOST
                user = USER
                pass = PASS
            }
        )
        setRemotePath(REMOTE_PATH)
    }

    @After
    fun tearDown() {
        localDir.listFiles()?.forEach { it.delete() }
        profiles.clearCurrent()
        profileBefore?.let(profiles::setCurrent)
        setRemotePath(pathBefore)
    }

    @Test
    fun aSyncStoresThePiconsAndSucceeds() {
        ftp.picons = listOf("ard.png")
        ftp.contents = mapOf("ard.png" to ARD)

        assertEquals(ListenableWorker.Result.success(), runSync())

        assertEquals(HOST, ftp.host)
        assertEquals(USER to PASS, ftp.credentials)
        assertEquals("the path comes from SettingsRepository", REMOTE_PATH, ftp.directory)
        assertArrayEquals(ARD, File(localDir, "ard.png").readBytes())
    }

    @Test
    fun aFailedLoginFailsTheWork() {
        ftp.loginReply = FtpReply(ok = false, code = 530, text = "Login incorrect")

        assertEquals(ListenableWorker.Result.failure(), runSync())

        assertNull("a failed login must not change directory", ftp.directory)
    }

    @Test
    fun aSyncThatStoredNothingFailsTheWork() {
        ftp.picons = listOf("ard.png")
        ftp.retrieveReply = FtpReply(ok = false, code = 550, text = "No such file")

        assertEquals(ListenableWorker.Result.failure(), runSync())

        assertEquals(
            context.getString(R.string.picon_sync_failed),
            completionNotificationText()
        )
    }

    @Test
    fun aPartialSyncSaysHowManyArrived() {
        ftp.picons = listOf("ard.png", "zdf.png")
        ftp.contents = mapOf("ard.png" to ARD)
        ftp.failRetrieveFor = setOf("zdf.png")

        assertEquals(ListenableWorker.Result.success(), runSync())

        assertEquals(
            context.getString(R.string.picon_sync_partial, 1, 2),
            completionNotificationText()
        )
    }

    @Test
    fun withoutAProfileItFailsBeforeConnecting() {
        profiles.clearCurrent()

        assertEquals(ListenableWorker.Result.failure(), runSync())

        assertNull(ftp.host)
    }

    private fun runSync(): ListenableWorker.Result = runBlocking {
        val worker = TestListenableWorkerBuilder
            .from(context, PiconSyncWorker::class.java)
            .build()
        worker.doWork()
    }

    /** The text of the completion notification the worker posts when it ends. */
    private fun completionNotificationText(): String? =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.id == PiconSync.COMPLETION_NOTIFICATION_ID }
            ?.notification
            ?.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()

    private fun setRemotePath(path: String) {
        settings.update { it.copy(syncPiconsPath = path) }
    }

    private companion object {
        const val HOST = "box.local"
        const val USER = "root"
        const val PASS = "secret"
        const val REMOTE_PATH = "/usr/share/enigma2/picon"

        val ARD = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
    }
}
