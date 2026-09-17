package net.reichholf.dreamdroid.helpers

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.enigma.EnigmaFailure
import okhttp3.Credentials
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnigmaHttpOkHttpTest {
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
    fun fetch_doesNotAppendSessionIdOntoCallerList() {
        server.enqueue(MockResponse().setBody("about-ok"))
        val client = clientForServer(sessionId = "abc")
        val params = ArrayList<NameValuePair>()
        params.add(NameValuePair("sRef", "1:0:1"))

        val result = client.fetch("/web/about", params)
        assertTrue(result is EnigmaHttpResult.Success)
        assertEquals("about-ok", (result as EnigmaHttpResult.Success).text)
        assertEquals(1, params.size)
        assertEquals("sRef", params[0].key())
        assertEquals("1:0:1", params[0].value())
    }

    @Test
    fun fetch_412RetrySendsOneFreshSessionId() {
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

        val result = client.fetch("/web/about", params)
        assertTrue(result is EnigmaHttpResult.Success)
        assertEquals("about-ok", (result as EnigmaHttpResult.Success).text)
        assertEquals(0, params.size)
    }

    @Test
    fun defaultConnectionTimeoutIsFifteenSeconds() {
        assertEquals(15_000, EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS)
        val client = clientForServer()
        assertEquals(
            EnigmaHttp.DEFAULT_CONNECTION_TIMEOUT_MILLIS,
            client.connectionTimeoutMillis()
        )
    }

    @Test
    fun fetch_survivesDelayPastFormerThreeSecondTimeout() {
        server.enqueue(
            MockResponse()
                .setHeadersDelay(4, TimeUnit.SECONDS)
                .setBody("slow-ok")
        )
        val client = clientForServer()
        val result = client.fetch("/web/about")
        assertTrue(result is EnigmaHttpResult.Success)
        assertEquals("slow-ok", (result as EnigmaHttpResult.Success).text)
    }

    @Test
    fun fetch_interruptCancelsSocket() {
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
        var failure: EnigmaFailure? = null
        val worker =
            Thread {
                when (val result = client.fetch("/web/about")) {
                    is EnigmaHttpResult.Success -> ok = true

                    is EnigmaHttpResult.Failure -> {
                        ok = false
                        failure = result.error.failure
                    }
                }
                finished.countDown()
            }
        try {
            worker.start()
            assertTrue(taken.await(5, TimeUnit.SECONDS))
            worker.interrupt()
            assertTrue(finished.await(3, TimeUnit.SECONDS))
            assertFalse(ok)
            assertEquals(EnigmaFailure.Cancelled, failure)
        } finally {
            hold.countDown()
        }
    }

    @Test
    fun fetch_overlapDoesNotSwapBodies() {
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
            when (val result = client.fetch("/web/a")) {
                is EnigmaHttpResult.Success -> {
                    firstOk = true
                    firstBody = result.text
                }

                is EnigmaHttpResult.Failure -> firstOk = false
            }
            firstDone.countDown()
        }.start()
        try {
            assertTrue(firstTaken.await(5, TimeUnit.SECONDS))
            val second = client.fetch("/web/b")
            assertTrue(second is EnigmaHttpResult.Success)
            assertEquals("BBB", (second as EnigmaHttpResult.Success).text)
        } finally {
            releaseFirst.countDown()
        }
        assertTrue(firstDone.await(5, TimeUnit.SECONDS))
        if (firstOk) {
            assertEquals("AAA", firstBody)
        }
    }

    @Test
    fun fetch_prefixesMissingSlash() {
        server.enqueue(MockResponse().setBody("ok"))
        val result = clientForServer().fetch("web/about")
        assertTrue(result is EnigmaHttpResult.Success)
        assertEquals("/web/about", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun fetch_postsWhenFeatureEnabled() {
        val previous = DreamDroid.featurePostRequest()
        DreamDroid.setFeaturePostRequest(true)
        try {
            server.enqueue(MockResponse().setBody("ok"))
            clientForServer().fetch("/web/about")
            assertEquals("POST", server.takeRequest().method)
        } finally {
            DreamDroid.setFeaturePostRequest(previous)
        }
    }

    @Test
    fun fetch_405RetriesAsGet() {
        val previous = DreamDroid.featurePostRequest()
        DreamDroid.setFeaturePostRequest(true)
        try {
            server.enqueue(MockResponse().setResponseCode(405))
            server.enqueue(MockResponse().setBody("ok-get"))
            val result = clientForServer().fetch("/web/about")
            assertTrue(result is EnigmaHttpResult.Success)
            assertEquals("ok-get", (result as EnigmaHttpResult.Success).text)
            assertEquals("POST", server.takeRequest().method)
            assertEquals("GET", server.takeRequest().method)
        } finally {
            DreamDroid.setFeaturePostRequest(previous)
        }
    }

    @Test
    fun fetch_sendsAuthorizationHeader() {
        server.enqueue(MockResponse().setBody("ok"))
        val profile =
            Profile().apply {
                host = "127.0.0.1"
                port = server.port
                ssl = false
                login = true
                user = "root"
                pass = "secret"
            }
        EnigmaHttp(profile).fetch("/web/about")
        assertEquals(
            Credentials.basic("root", "secret"),
            server.takeRequest().getHeader("Authorization")
        )
    }

    @Test
    fun fetch_screenshotOmitsSessionId() {
        server.enqueue(MockResponse().setBody("png"))
        val result = clientForServer(sessionId = "abc").fetch("/grab?")
        assertTrue(result is EnigmaHttpResult.Success)
        assertNull(server.takeRequest().requestUrl!!.queryParameter("sessionid"))
    }

    @Test
    fun fetch_httpErrorIsFailure() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val result = clientForServer().fetch("/web/about")
        assertTrue(result is EnigmaHttpResult.Failure)
        val failure = (result as EnigmaHttpResult.Failure).error.failure
        assertTrue(failure is EnigmaFailure.Http)
        assertEquals(500, (failure as EnigmaFailure.Http).code)
    }

    @Test
    fun fetch_401IsAuthFailure() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
        val result = clientForServer().fetch("/web/about")
        assertTrue(result is EnigmaHttpResult.Failure)
        assertEquals(EnigmaFailure.Auth, (result as EnigmaHttpResult.Failure).error.failure)
    }

    @Test
    fun fetch_refusedConnectionIsFailure() {
        val profile =
            Profile().apply {
                host = "127.0.0.1"
                port = 1
                ssl = false
                login = false
            }
        val result = EnigmaHttp(profile, timeoutMillis = 2_000).fetch("/web/about")
        assertTrue(result is EnigmaHttpResult.Failure)
    }

    @Test
    fun httpClientDoesNotInstallProcessSslDefaults() {
        val beforeFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val beforeVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        val profile = Profile().apply {
            host = "box.local"
            port = 443
            ssl = true
            allCertsTrusted = true
        }
        EnigmaHttp(profile)
        assertEquals(beforeFactory, HttpsURLConnection.getDefaultSSLSocketFactory())
        assertEquals(beforeVerifier, HttpsURLConnection.getDefaultHostnameVerifier())
    }

    private fun clientForServer(sessionId: String? = null): EnigmaHttp {
        val profile =
            Profile().apply {
                host = "127.0.0.1"
                port = server.port
                ssl = false
                login = false
                this.sessionId = sessionId
            }
        return EnigmaHttp(profile)
    }
}
