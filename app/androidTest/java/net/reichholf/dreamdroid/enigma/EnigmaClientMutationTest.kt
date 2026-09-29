package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.Python
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Typed [EnigmaClient] mutations hit the right endpoint and map the simple XML result. */
@RunWith(AndroidJUnit4::class)
class EnigmaClientMutationTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun zapSendsServiceReferenceAndSucceeds() = runBlocking {
        server.enqueue(MockResponse().setBody(simpleResult(Python.TRUE, "Active service is now")))
        val response = client().zap(listOf(NameValuePair("sRef", "1:0:1:a")))
        val request = server.takeRequest()
        assertEquals("/web/zap", request.requestUrl!!.encodedPath)
        assertEquals("1:0:1:a", request.requestUrl!!.queryParameter("sRef"))
        assertEquals("Active service is now", response.value!!.stateText)
        assertNull(response.error)
    }

    @Test
    fun rejectedTimerChangeIsBoxRejected() = runBlocking {
        server.enqueue(MockResponse().setBody(simpleResult(Python.FALSE, "Conflicting timer")))
        val response = client().changeTimer(listOf(NameValuePair("sRef", "1:0:1:a")))
        assertEquals("/web/timerchange", server.takeRequest().requestUrl!!.encodedPath)
        assertNotNull(response.value)
        assertEquals(EnigmaFailure.BoxRejected("Conflicting timer"), response.error!!.failure)
    }

    @Test
    fun cleanupTimersKeepsFixedQuery() = runBlocking {
        server.enqueue(MockResponse().setBody(simpleResult(Python.TRUE, "List was cleaned up")))
        client().cleanupTimers()
        val url = server.takeRequest().requestUrl!!
        assertEquals("/web/timercleanup", url.encodedPath)
        assertEquals("true", url.queryParameter("cleanup"))
    }

    @Test
    fun httpFailureIsNotSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val response = client().deleteMovie(listOf(NameValuePair("sRef", "1:0:0:m")))
        assertEquals("/web/moviedelete", server.takeRequest().requestUrl!!.encodedPath)
        assertNull(response.value)
        assertTrue(response.error!!.failure is EnigmaFailure.Http)
    }

    @Test
    fun resultWithoutStateTextIsNotSuccess() = runBlocking {
        server.enqueue(MockResponse().setBody("<e2simplexmlresult><e2state>True</e2state>"))
        val response = client().remoteCommand(listOf(NameValuePair("command", "352")))
        assertEquals("/web/remotecontrol", server.takeRequest().requestUrl!!.encodedPath)
        assertNull(response.value)
        assertNull(response.error)
    }

    @Test
    fun setVolumeParsesVolume() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "<e2volume><e2result>True</e2result><e2current>40</e2current>" +
                    "<e2ismuted>False</e2ismuted></e2volume>"
            )
        )
        val volume = client().setVolume(listOf(NameValuePair("set", "up"))).value!!
        assertEquals("/web/vol", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals("40", volume.current)
    }

    private fun client() = EnigmaClient(
        EnigmaHttp(
            Profile().apply {
                host = "127.0.0.1"
                port = server.port
                ssl = false
                login = false
            },
            EnigmaOkHttp()
        )
    )

    private fun simpleResult(state: String, stateText: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><e2simplexmlresult>" +
            "<e2state>$state</e2state><e2statetext>$stateText</e2statetext>" +
            "</e2simplexmlresult>"
}
