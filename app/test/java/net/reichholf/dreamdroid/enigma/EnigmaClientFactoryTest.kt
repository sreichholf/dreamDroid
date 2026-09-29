package net.reichholf.dreamdroid.enigma

import androidx.preference.PreferenceManager
import java.io.File
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.testutil.TestProfiles
import net.reichholf.dreamdroid.testutil.enigmaClients
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [EnigmaClientFactory]'s HTTP: the active profile and the "dump XML" setting. */
class EnigmaClientFactoryTest {
    private val server = MockWebServer()
    private val profiles = TestProfiles()
    private val repository = profiles.repository
    private val clients = enigmaClients(repository, profiles.context)
    private val dumpDir = File(profiles.context.cacheDir, "xml")

    @BeforeEach
    fun setUp() {
        server.start()
        server.enqueue(MockResponse().setBody("<e2about/>"))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun currentHttpTalksToTheActiveProfile() {
        activate(xmlDebug = false)

        val result = clients.currentHttp().fetch("/web/about")

        assertTrue(result is EnigmaHttpResult.Success)
        assertEquals("/web/about", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun xmlDebugDumpsResponsesIntoTheCache() {
        activate(xmlDebug = true)

        clients.currentHttp().fetch("/web/about")

        assertEquals("<e2about/>", File(dumpDir, "about").readText())
    }

    @Test
    fun withoutXmlDebugNothingIsDumped() {
        activate(xmlDebug = false)

        clients.currentHttp().fetch("/web/about")

        assertFalse(dumpDir.exists())
    }

    private fun activate(xmlDebug: Boolean) {
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
}
