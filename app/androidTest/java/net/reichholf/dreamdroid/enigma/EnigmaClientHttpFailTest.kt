package net.reichholf.dreamdroid.enigma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore
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
        previousProfile = DreamDroid.currentProfileOrNull()
        DreamDroid.setCurrentProfile(profileForServer())
    }

    @After
    fun stopServer() {
        val previous = previousProfile
        if (previous != null) {
            DreamDroid.setCurrentProfile(previous)
        } else {
            DreamDroid.loadCurrentProfile(appContext())
        }
        server.shutdown()
    }

    @Test
    fun getServices_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val services = EnigmaClient(profileForServer()).getServices().value
        assertEquals(null, services)
    }

    @Test
    fun getServices_http500IsHttpFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val response = EnigmaClient(profileForServer()).getServices()
        assertEquals(null, response.value)
        val failure = response.error!!.failure
        assertTrue(failure is EnigmaFailure.Http)
        assertEquals(500, (failure as EnigmaFailure.Http).code)
    }

    @Test
    fun getServices_http401IsAuthFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
        val response = EnigmaClient(profileForServer()).getServices()
        assertEquals(null, response.value)
        assertEquals(EnigmaFailure.Auth, response.error!!.failure)
    }

    @Test
    fun getServices_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val services = EnigmaClient(profileForServer()).getServices().value
        assertEquals(emptyList<Service>(), services)
    }

    @Test
    fun getServices_200ReturnsFixtureNames() = runBlocking {
        server.enqueue(MockResponse().setBody(loadWebFixture("getservices.xml")))
        val services = EnigmaClient(profileForServer()).getServices().value!!
        assertEquals(3, services.size)
        assertEquals("Favourites (TV)", services[0].name)
        assertEquals("Das Erste HD", services[1].name)
        assertEquals("--------", services[2].name)
    }

    @Test
    fun getEvents_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val events = EnigmaClient(profileForServer()).getEvents().value
        assertEquals(null, events)
    }

    @Test
    fun getEvents_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val events = EnigmaClient(profileForServer()).getEvents().value
        assertEquals(emptyList<Event>(), events)
    }

    @Test
    fun getEpgNowNext_httpFailIsNull() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val rows = EnigmaClient(profileForServer()).getEpgNowNext().value
        assertEquals(null, rows)
    }

    @Test
    fun getEpgNowNext_empty200IsEmptyList() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val rows = EnigmaClient(profileForServer()).getEpgNowNext().value
        assertEquals(emptyList<ServiceNowNext>(), rows)
    }

    @Test
    fun loadServiceList_httpFailIsNotEmptySuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val result = loadServiceList(appContext(), listOf(NameValuePair("sRef", "1:0:1")))
        assertEquals(false, result.success)
        assertEquals(emptyList<Service>(), result.services)
        assertEquals(contentError("Server Error"), result.errorText)
    }

    @Test
    fun loadServiceList_empty200IsSuccess() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val result = loadServiceList(appContext(), listOf(NameValuePair("sRef", "1:0:1")))
        assertEquals(true, result.success)
        assertEquals(emptyList<Service>(), result.services)
        assertEquals(null, result.errorText)
    }

    @Test
    fun loadEventList_httpFailIsNotEmptySuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val result = loadEventList(appContext(), emptyList(), URIStore.EPG_SERVICE)
        assertEquals(false, result.success)
        assertEquals(emptyList<Event>(), result.events)
        assertEquals(contentError("Server Error"), result.errorText)
    }

    @Test
    fun loadEpgNowNext_httpFailIsNotEmptySuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        val result = loadEpgNowNext(appContext(), listOf(NameValuePair("bRef", "1:7:1")))
        assertEquals(false, result.success)
        assertEquals(emptyList<ServiceNowNext>(), result.rows)
        assertEquals(contentError("Server Error"), result.errorText)
    }

    @Test
    fun loadBouquetList_tvHttpFailIsFailureEvenIfRadioSucceeds() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("nope"))
        server.enqueue(MockResponse().setBody(loadWebFixture("getservices.xml")))
        val result = loadBouquetList(appContext())
        assertEquals(false, result.success)
        assertEquals(0, result.bouquets.tv.size)
        assertEquals(contentError("Server Error"), result.errorText)
    }

    @Test
    fun loadBouquetList_emptyRootsAreSuccess() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        server.enqueue(MockResponse().setBody(""))
        val result = loadBouquetList(appContext())
        assertEquals(true, result.success)
        assertEquals(0, result.bouquets.tv.size)
        assertEquals(0, result.bouquets.radio.size)
        assertEquals(null, result.errorText)
    }

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun contentError(httpMessage: String): String =
        appContext().getString(R.string.get_content_error) + "\n" + httpMessage

    private fun profileForServer(): Profile = Profile().apply {
        host = "127.0.0.1"
        port = server.port
        ssl = false
        login = false
    }
}
