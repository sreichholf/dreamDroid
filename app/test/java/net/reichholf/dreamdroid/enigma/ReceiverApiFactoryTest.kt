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

/** [ReceiverApiFactory]'s clients: the active profile and the "dump XML" setting. */
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

    private fun activate(xmlDebug: Boolean) = runBlocking<Unit> {
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
    }

    private companion object {
        const val DEVICE_INFO =
            "<e2deviceinfo><e2devicename>dm920</e2devicename></e2deviceinfo>"
    }
}
