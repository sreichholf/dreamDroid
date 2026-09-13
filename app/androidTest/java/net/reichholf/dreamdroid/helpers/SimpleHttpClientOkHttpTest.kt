package net.reichholf.dreamdroid.helpers

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.reichholf.dreamdroid.Profile
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimpleHttpClientOkHttpTest {
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
    fun buildUrl_httpHostAndQuery() {
        val profile = Profile().apply {
            host = "box.local"
            port = 80
            ssl = false
            login = false
        }
        val client = SimpleHttpClient.getInstance(profile)
        val url = client.buildUrl("/web/about", ArrayList())
        assertEquals("http://box.local:80/web/about?", url)
    }

    @Test
    fun buildUrl_httpsWhenSsl() {
        val profile = Profile().apply {
            host = "box.local"
            port = 443
            ssl = true
            login = false
        }
        val client = SimpleHttpClient.getInstance(profile)
        val url = client.buildUrl("/web/about", ArrayList())
        assertTrue(url.startsWith("https://"))
        assertTrue(url.contains("box.local:443/web/about"))
    }

    @Test
    fun buildAuthedUrl_embedsUserInfo() {
        val profile = Profile().apply {
            host = "box.local"
            port = 80
            ssl = false
            login = true
            user = "root"
            pass = "secret"
        }
        val client = SimpleHttpClient.getInstance(profile)
        val url = client.buildAuthedUrl("/web/about", ArrayList())
        assertTrue(url.contains("root:secret@box.local:80"))
    }

    @Test
    fun fetchPageContent_doesNotAppendSessionIdOntoCallerList() {
        server.enqueue(MockResponse().setBody("about-ok"))
        val client = clientForServer(sessionId = "abc")
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("sRef", "1:0:1"))

        assertTrue(client.fetchPageContent("/web/about", params))
        assertEquals("about-ok", client.pageContentString)
        assertEquals(1, params.size)
        assertEquals("sRef", params[0].key())
        assertEquals("1:0:1", params[0].value())
    }

    @Test
    fun fetchPageContent_412RetrySendsOneFreshSessionId() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val url = request.requestUrl!!
                    val sessionIds = url.queryParameterValues("sessionid")
                    return when {
                        url.encodedPath == "/web/session" -> MockResponse().setBody("fresh-id")
                        url.encodedPath == "/web/about" && sessionIds == listOf("stale") ->
                            MockResponse().setResponseCode(412)
                        url.encodedPath == "/web/about" && sessionIds == listOf("fresh-id") ->
                            MockResponse().setBody("about-ok")
                        else -> MockResponse().setResponseCode(500).setBody("bad-session-query")
                    }
                }
            }
        val client = clientForServer(sessionId = "stale")
        val params = ArrayList<NameValuePair>()

        assertTrue(client.fetchPageContent("/web/about", params))
        assertEquals("about-ok", client.pageContentString)
        assertEquals(0, params.size)
    }

    @Test
    fun fetchPageContent_interruptCancelsSocket() {
        val taken = CountDownLatch(1)
        val hold = CountDownLatch(1)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    taken.countDown()
                    hold.await()
                    return MockResponse().setBody("late")
                }
            }
        val client = clientForServer()
        client.setConnectionTimeoutMillis(15_000)
        val finished = CountDownLatch(1)
        var ok = true
        val worker =
            Thread {
                ok = client.fetchPageContent("/web/about")
                finished.countDown()
            }
        try {
            worker.start()
            assertTrue(taken.await(5, TimeUnit.SECONDS))
            worker.interrupt()
            assertTrue(finished.await(3, TimeUnit.SECONDS))
            assertFalse(ok)
            assertTrue(client.hasError())
        } finally {
            hold.countDown()
        }
    }

    @Test
    fun fetchPageContent_overlapDoesNotSwapBodies() {
        val firstTaken = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    return if (path == "/web/a") {
                        firstTaken.countDown()
                        releaseFirst.await()
                        MockResponse().setBody("AAA")
                    } else {
                        MockResponse().setBody("BBB")
                    }
                }
            }
        val client = clientForServer()
        val firstDone = CountDownLatch(1)
        var firstOk = false
        var firstBody = ""
        Thread {
            firstOk = client.fetchPageContent("/web/a")
            firstBody = client.pageContentString
            firstDone.countDown()
        }.start()
        try {
            assertTrue(firstTaken.await(5, TimeUnit.SECONDS))
            assertTrue(client.fetchPageContent("/web/b"))
            assertEquals("BBB", client.pageContentString)
        } finally {
            releaseFirst.countDown()
        }
        assertTrue(firstDone.await(5, TimeUnit.SECONDS))
        if (firstOk) {
            assertEquals("AAA", firstBody)
        }
        assertEquals("BBB", client.pageContentString)
    }

    private fun clientForServer(sessionId: String? = null): SimpleHttpClient {
        val profile =
            Profile().apply {
                host = "127.0.0.1"
                port = server.port
                ssl = false
                login = false
                this.sessionId = sessionId
            }
        return SimpleHttpClient.getInstance(profile)
    }
}
