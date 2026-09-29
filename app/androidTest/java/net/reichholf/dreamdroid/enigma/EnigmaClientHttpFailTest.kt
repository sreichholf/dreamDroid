package net.reichholf.dreamdroid.enigma

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.data.ProfileRepository
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
class EnigmaClientHttpFailTest {
    private lateinit var server: MockWebServer
    private var previousProfile: Profile? = null

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
        previousProfile = ProfileRepository.get().current.value
        ProfileRepository.get().setCurrent(profileForServer())
    }

    @After
    fun stopServer() {
        val previous = previousProfile
        if (previous != null) {
            ProfileRepository.get().setCurrent(previous)
        } else {
            ProfileRepository.get().loadCurrent()
        }
        server.shutdown()
    }

    @Test
    fun getServices_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val services = client().getServices().value
        assertEquals(null, services)
    }

    @Test
    fun getServices_http500IsHttpFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val response = client().getServices()
        assertEquals(null, response.value)
        val failure = response.error!!.failure
        assertTrue(failure is EnigmaFailure.Http)
        assertEquals(500, (failure as EnigmaFailure.Http).code)
    }

    @Test
    fun getServices_http401IsAuthFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
        val response = client().getServices()
        assertEquals(null, response.value)
        assertEquals(EnigmaFailure.Auth, response.error!!.failure)
    }

    @Test
    fun getServices_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val services = client().getServices().value
        assertEquals(emptyList<Service>(), services)
    }

    @Test
    fun getServices_200ReturnsFixtureNames() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("getservices.xml")))
        val services = client().getServices().value!!
        assertEquals(3, services.size)
        assertEquals("Favourites (TV)", services[0].name)
        assertEquals("Das Erste HD", services[1].name)
        assertEquals("--------", services[2].name)
    }

    @Test
    fun getEvents_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val events = client().getEvents().value
        assertEquals(null, events)
    }

    @Test
    fun getEvents_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val events = client().getEvents().value
        assertEquals(emptyList<Event>(), events)
    }

    @Test
    fun getEpgNowNext_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val rows = client().getEpgNowNext().value
        assertEquals(null, rows)
    }

    @Test
    fun getEpgNowNext_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val rows = client().getEpgNowNext().value
        assertEquals(emptyList<ServiceNowNext>(), rows)
    }

    private fun profileForServer(): Profile = Profile().apply {
        host = "127.0.0.1"
        port = server.port
        ssl = false
        login = false
    }

    private fun client() = EnigmaClient(EnigmaHttp(profileForServer(), EnigmaOkHttp()))
}
