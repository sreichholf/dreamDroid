package net.reichholf.dreamdroid.enigma

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The `/web` requests [DreamboxWebIfApi] sends for the services and EPG calls. */
class DreamboxWebIfApiTest {
    private val server = MockWebServer()

    private val api by lazy {
        DreamboxWebIfApi(
            EnigmaHttp(
                Profile().apply {
                    host = server.hostName
                    port = server.port
                },
                EnigmaOkHttp()
            )
        )
    }

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        DreamDroid.enableNowNext()
        server.shutdown()
    }

    @Test
    fun epgNowNextAsksEpgNowNextByBouquet() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        val rows = api.epgNowNext(BOUQUET).value!!

        val url = takeUrl()
        assertEquals("/web/epgnownext", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertNull(url.queryParameter("sRef"))
        assertEquals(1, rows.size)
    }

    @Test
    fun withoutNowNextEpgNowNextAsksEpgNowWithOneRowPerEvent() = runBlocking {
        DreamDroid.disableNowNext()
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        val rows = api.epgNowNext(BOUQUET).value!!

        val url = takeUrl()
        assertEquals("/web/epgnow", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.now != null && it.next == null })
    }

    @Test
    fun epgNowNextAsksAProviderByBRef() = runBlocking {
        DreamDroid.disableNowNext()
        server.enqueue(MockResponse().setBody(loadWebFixture("epgservice.xml")))

        api.epgNowNext(PROVIDERS)

        val url = takeUrl()
        assertEquals("/web/epgnow", url.encodedPath)
        assertEquals(PROVIDERS, url.queryParameter("bRef"))
        assertNull(url.queryParameter("sRef"))
    }

    @Test
    fun epgMultiSendsTheWindowLengthInWholeMinutesRoundedDown() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.epgMulti(BOUQUET, START, START + 90 * 60 + 59)
        api.epgMulti(BOUQUET, START, START + 30)

        val url = takeUrl()
        assertEquals("/web/epgmulti", url.encodedPath)
        assertEquals(BOUQUET, url.queryParameter("bRef"))
        assertEquals(START.toString(), url.queryParameter("time"))
        assertEquals("90", url.queryParameter("endTime"))
        assertEquals("1", takeUrl().queryParameter("endTime"))
    }

    @Test
    fun serviceEpgWindowSendsItsLengthInWholeMinutesRoundedUp() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))

        api.serviceEpg(SERVICE, START, START + 61)
        api.serviceEpg(SERVICE, START, START)

        val url = takeUrl()
        assertEquals("/web/epgservice", url.encodedPath)
        assertEquals(SERVICE, url.queryParameter("sRef"))
        assertEquals(START.toString(), url.queryParameter("time"))
        assertEquals("2", url.queryParameter("endTime"))
        assertEquals("1", takeUrl().queryParameter("endTime"))
    }

    @Test
    fun serviceEpgWithoutAWindowSendsOnlyTheService() = runBlocking {
        server.enqueue(MockResponse().setBody(""))

        api.serviceEpg(SERVICE)

        val url = takeUrl()
        assertEquals("/web/epgservice", url.encodedPath)
        assertEquals(setOf("sRef"), url.queryParameterNames)
    }

    private fun takeUrl(): HttpUrl = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!

    private companion object {
        const val BOUQUET = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\""
        const val PROVIDERS = "1:0:1:0:0:0:0:0:0:0:FROM PROVIDERS"
        const val SERVICE = "1:0:19:2B66:3F3:1:C00000:0:0:0:"
        const val START = 1_700_000_000L
    }
}
