package net.reichholf.dreamdroid.enigma

import androidx.preference.PreferenceManager
import java.io.File
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.receiverApis
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [ReceiverApiFactory]'s clients: the active profile, its detected flavor, and the "dump XML"
 * setting.
 */
class ReceiverApiFactoryTest {
    private val server = MockWebServer()
    private val profiles = TestProfiles()
    private val repository = profiles.repository
    private val clients = receiverApis(repository, profiles.context)
    private val dumpDir = File(profiles.context.cacheDir, "xml")

    @BeforeEach
    fun setUp() {
        server.start()
        server.enqueue(MockResponse().setBody(DEVICE_INFO))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun currentTalksToTheActiveProfile() = runBlocking {
        activate(xmlDebug = false)

        val info = clients.current().deviceInfo().value

        assertEquals("dm920", info?.deviceName)
        assertEquals("/web/deviceinfo", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun xmlDebugDumpsResponsesIntoTheCache() = runBlocking {
        activate(xmlDebug = true)

        clients.current().deviceInfo()

        assertEquals(DEVICE_INFO, File(dumpDir, "deviceinfo").readText())
    }

    @Test
    fun withoutXmlDebugNothingIsDumped() = runBlocking {
        activate(xmlDebug = false)

        clients.current().deviceInfo()

        assertFalse(dumpDir.exists())
    }

    @Test
    fun anOpenWebifProfileGetsTheOpenWebifClient() = runBlocking {
        val profile = activate(xmlDebug = false)
        server.enqueue(MockResponse().setBody("{}"))

        repository.setDeviceInfo(profile, DeviceInfo(deviceName = "x"), ReceiverFlavor.OpenWebif)
        clients.current().deviceInfo()
        clients.forProfile(profile).deviceInfo()

        assertEquals("/api/deviceinfo", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("/api/deviceinfo", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun aDreamboxOrUndetectedProfileGetsTheDreamboxClient() = runBlocking {
        val profile = activate(xmlDebug = false)
        server.enqueue(MockResponse().setBody(DEVICE_INFO))

        clients.current().deviceInfo()
        repository.setDeviceInfo(
            profile,
            DeviceInfo(deviceName = "x"),
            ReceiverFlavor.DreamboxWebIf
        )
        clients.current().deviceInfo()

        assertEquals("/web/deviceinfo", server.takeRequest().requestUrl?.encodedPath)
        assertEquals("/web/deviceinfo", server.takeRequest().requestUrl?.encodedPath)
    }

    private fun activate(xmlDebug: Boolean) = runBlocking<Profile> {
        PreferenceManager.getDefaultSharedPreferences(profiles.context).edit()
            .putBoolean(DreamDroid.PREFS_KEY_XML_DEBUG, xmlDebug)
            .commit()
        val profile = Profile().apply {
            name = "test"
            host = server.hostName
            port = server.port
        }
        repository.save(profile)
        assertTrue(repository.setCurrent(profile.id!!))
        profile
    }

    private companion object {
        const val DEVICE_INFO =
            "<e2deviceinfo><e2devicename>dm920</e2devicename></e2deviceinfo>"
    }
}
