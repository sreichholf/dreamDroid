package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.WebIfCapabilitiesRepository
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaOkHttp
import net.reichholf.dreamdroid.testutil.loadWebFixture
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DreamboxWebIfApiHttpFailTest {
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
    fun services_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val services = client().services(REF).value
        assertEquals(null, services)
    }

    @Test
    fun services_http500IsHttpFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val response = client().services(REF)
        assertEquals(null, response.value)
        val failure = response.error!!.failure
        assertTrue(failure is EnigmaFailure.Http)
        assertEquals(500, (failure as EnigmaFailure.Http).code)
    }

    @Test
    fun services_http401IsAuthFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
        val response = client().services(REF)
        assertEquals(null, response.value)
        assertEquals(EnigmaFailure.Auth, response.error!!.failure)
    }

    @Test
    fun services_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val services = client().services(REF).value
        assertEquals(emptyList<Service>(), services)
    }

    @Test
    fun services_200ReturnsFixtureNames() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("getservices.xml")))
        val services = client().services(REF).value!!
        assertEquals(3, services.size)
        assertEquals("Favourites (TV)", services[0].name)
        assertEquals("Das Erste HD", services[1].name)
        assertEquals("--------", services[2].name)
    }

    @Test
    fun serviceEpg_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val events = client().serviceEpg(REF).value
        assertEquals(null, events)
    }

    @Test
    fun serviceEpg_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val events = client().serviceEpg(REF).value
        assertEquals(emptyList<Event>(), events)
    }

    @Test
    fun epgNowNext_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val rows = client().epgNowNext(REF).value
        assertEquals(null, rows)
    }

    @Test
    fun epgNowNext_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val rows = client().epgNowNext(REF).value
        assertEquals(emptyList<ServiceNowNext>(), rows)
    }

    private fun profileForServer(): Profile = Profile().apply {
        host = "127.0.0.1"
        port = server.port
        ssl = false
        login = false
    }

    private fun client() = DreamboxWebIfApi(
        EnigmaHttp(profileForServer(), EnigmaOkHttp(), WebIfCapabilitiesRepository()),
        WebIfCapabilities()
    )

    private companion object {
        const val REF = "1:7:1:0:0:0:0:0:0:0:FROM BOUQUET \"userbouquet.favourites.tv\""
    }
}
